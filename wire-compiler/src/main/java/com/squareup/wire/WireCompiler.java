/*
 * Copyright 2013 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.wire;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import com.squareup.wire.java.JavaGenerator;
import com.squareup.wire.java.TypeWriter;
import com.squareup.wire.model.Linker;
import com.squareup.wire.model.Loader;
import com.squareup.wire.model.Pruner;
import com.squareup.wire.model.WireProtoFile;
import com.squareup.wire.model.WireService;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Compiler for Wire protocol buffers. */
public class WireCompiler {
  private static final String CODE_GENERATED_BY_WIRE =
      "Code generated by Wire protocol buffer compiler, do not edit.";

  private final String repoPath;
  private final IO io;
  private final CommandLineOptions options;
  private final WireLogger log;

  /**
   * Runs the compiler.  See {@link CommandLineOptions} for command line options.
   */
  public static void main(String... args) {
    try {
      new WireCompiler(new CommandLineOptions(args)).compile();
    } catch (WireException e) {
      System.err.print("Fatal: ");
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  WireCompiler(CommandLineOptions options) throws WireException {
    this(options, new IO.FileIO(), new ConsoleWireLogger(options.quiet));
  }

  WireCompiler(CommandLineOptions options, IO io, WireLogger logger) throws WireException {
    this.options = options;
    this.io = io;
    this.log = logger;

    String protoPath = options.protoPath;
    if (options.javaOut == null) {
      throw new WireException("Must specify " + CommandLineOptions.JAVA_OUT_FLAG + " flag");
    }
    if (options.protoPath == null) {
      protoPath = System.getProperty("user.dir");
      System.err.println(
          CommandLineOptions.PROTO_PATH_FLAG + " flag not specified, using current dir "
              + protoPath);
    }
    this.repoPath = protoPath;
  }

  public void compile() throws WireException {
    Set<String> parsedFiles = new LinkedHashSet<String>();
    Loader loader = new Loader(repoPath, io);
    for (String sourceFilename : options.sourceFileNames) {
      String sourcePath = repoPath + File.separator + sourceFilename;
      parsedFiles.add(sourcePath);
      try {
        loader.add(sourceFilename);
      } catch (IOException e) {
        throw new WireException("Error loading symbols for " + sourcePath, e);
      }
    }

    List<WireProtoFile> wireProtoFiles = loader.loaded();
    Linker linker = new Linker();
    linker.link(wireProtoFiles);

    if (!options.roots.isEmpty()) {
      log.info("Analyzing dependencies of root types.");
      wireProtoFiles = new Pruner().retainRoots(wireProtoFiles, options.roots);
    }

    JavaGenerator javaGenerator = JavaGenerator.get(wireProtoFiles);
    TypeWriter typeWriter = new TypeWriter(
        javaGenerator, options.emitOptions, options.enumOptions);

    for (WireProtoFile wireProtoFile : wireProtoFiles) {
      if (!parsedFiles.contains(wireProtoFile.sourcePath())) {
        continue; // Don't emit anything for files not explicitly compiled.
      }

      for (com.squareup.wire.model.WireType type : wireProtoFile.types()) {
        ClassName javaTypeName = (ClassName) javaGenerator.typeName(type.protoTypeName());
        TypeSpec typeSpec = typeWriter.toTypeSpec(type);
        writeJavaFile(javaTypeName, typeSpec, wireProtoFile.sourcePath());
      }

      if (options.serviceFactory != null) {
        for (WireService service : wireProtoFile.services()) {
          TypeSpec typeSpec = options.serviceFactory.create(
              javaGenerator, options.serviceFactoryOptions, service);
          ClassName baseJavaTypeName = (ClassName) javaGenerator.typeName(service.protoTypeName());
          // Use 'peerClass' to track service factories that add a prefix or suffix.
          ClassName generatedJavaTypeName = baseJavaTypeName.peerClass(typeSpec.name);
          writeJavaFile(generatedJavaTypeName, typeSpec, wireProtoFile.sourcePath());
        }
      }

      if (!wireProtoFile.wireExtends().isEmpty()) {
        ClassName javaTypeName = javaGenerator.extensionsClass(wireProtoFile);
        TypeSpec typeSpec = typeWriter.extensionsType(javaTypeName, wireProtoFile);
        writeJavaFile(javaTypeName, typeSpec, wireProtoFile.sourcePath());
      }
    }

    if (options.registryClass != null) {
      ClassName className = ClassName.bestGuess(options.registryClass);
      TypeSpec typeSpec = typeWriter.registryType(className, wireProtoFiles);
      writeJavaFile(className, typeSpec, null);
    }
  }

  private void writeJavaFile(
      ClassName javaTypeName, TypeSpec typeSpec, String sourceFileName) throws WireException {
    JavaFile.Builder builder = JavaFile.builder(javaTypeName.packageName(), typeSpec)
        .addFileComment("$L", CODE_GENERATED_BY_WIRE);
    if (sourceFileName != null) {
      builder.addFileComment("\nSource file: $L", sourceFileName);
    }
    JavaFile javaFile = builder.build();

    log.artifact(options.javaOut, javaFile);

    try {
      if (!options.dryRun) {
        io.write(options.javaOut, javaFile);
      }
    } catch (IOException e) {
      throw new WireException("Error emitting " + javaFile.packageName + "."
          + javaFile.typeSpec.name + " to " + options.javaOut, e);
    }
  }
}
