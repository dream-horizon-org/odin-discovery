package com.dream11.odin;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.config.ApplicationConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.grpc.provideraccount.v1.Rx3ProviderAccountServiceGrpc;
import com.dream11.odin.util.ContextUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.inject.AbstractModule;
import io.vertx.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;

public class MainModule extends AbstractModule {

  protected final Vertx vertx;

  private final ObjectMapper objectMapper =
      JsonMapper.builder()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
          .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
          .serializationInclusion(JsonInclude.Include.NON_NULL)
          .build();

  public MainModule(Vertx vertx) {
    this.vertx = vertx;
  }

  @Override
  protected void configure() {
    bind(MysqlClient.class).toProvider(() -> ContextUtil.getInstance(Constants.MYSQL_CLIENT));
    bind(ObjectMapper.class).toInstance(objectMapper);
    bind(Rx3ProviderAccountServiceGrpc.RxProviderAccountServiceStub.class)
        .toProvider(() -> ContextUtil.getInstance(Constants.PROVIDER_ACCOUNT_SERVICE));
    bind(WebClient.class).toProvider(() -> ContextUtil.getInstance(Constants.WEB_CLIENT));
    bind(ApplicationConfig.class)
        .toProvider(() -> SharedDataUtil.getInstance(ApplicationConfig.class));
  }
}
