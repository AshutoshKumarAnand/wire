// Code generated by Wire protocol buffer compiler, do not edit.
// Source file: ../wire-runtime/src/test/proto/rxjava_service2.proto
package com.squareup.services;

import com.squareup.services.anotherpackage.SendDataRequest;
import com.squareup.services.anotherpackage.SendDataResponse;
import java.lang.Override;
import javax.inject.Inject;
import retrofit.http.Body;
import retrofit.http.POST;
import rx.functions.Func1;

/**
 * An example service.
 */
public final class RxJavaService2SomeEndpoints {
  private final Func1<SendDataRequest, SendDataResponse> sendSomeMoreData = new Func1<SendDataRequest, SendDataResponse>() {
    @Override
    public SendDataResponse call(SendDataRequest request) {
      return endpoint.sendSomeMoreData(request);
    }
  };

  private final Func1<LetsDataRequest, LetsDataResponse> letsData = new Func1<LetsDataRequest, LetsDataResponse>() {
    @Override
    public LetsDataResponse call(LetsDataRequest request) {
      return endpoint.letsData(request);
    }
  };

  private final Endpoint endpoint;

  @Inject
  public RxJavaService2SomeEndpoints(Endpoint endpoint) {
    this.endpoint = endpoint;
  }

  public Func1<SendDataRequest, SendDataResponse> getSendSomeMoreData() {
    return sendSomeMoreData;
  }

  public Func1<LetsDataRequest, LetsDataResponse> getLetsData() {
    return letsData;
  }

  public interface Endpoint {
    /**
     * Sends some more data.
     */
    @POST("/com.squareup.services.RxJavaService2/SendSomeMoreData")
    SendDataResponse sendSomeMoreData(@Body SendDataRequest request);

    /**
     * Sends even more data.
     */
    @POST("/com.squareup.services.RxJavaService2/LetsData")
    LetsDataResponse letsData(@Body LetsDataRequest request);
  }
}
