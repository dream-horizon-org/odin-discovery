package com.dream11.odin.setup;

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
import lombok.SneakyThrows;
import org.junit.jupiter.api.extension.ExtensionContext;

public class TestModule extends AbstractModule {

  Vertx vertx;
  ExtensionContext extensionContext;

  private final ObjectMapper objectMapper =
      JsonMapper.builder()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
          .serializationInclusion(JsonInclude.Include.NON_NULL)
          .build();

  public TestModule(Vertx vertx, ExtensionContext extensionContext) {
    this.vertx = vertx;
    this.extensionContext = extensionContext;
  }

  @SneakyThrows
  @Override
  protected void configure() {
    bind(Vertx.class).toInstance(this.vertx);
    bind(ObjectMapper.class).toInstance(this.objectMapper);
    bind(MysqlClient.class).toProvider(() -> ContextUtil.getInstance(Constants.MYSQL_CLIENT));
    bind(WebClient.class).toProvider(() -> ContextUtil.getInstance(Constants.WEB_CLIENT));
    bind(ApplicationConfig.class)
        .toProvider(() -> SharedDataUtil.getInstance(ApplicationConfig.class));
    bind(Rx3ProviderAccountServiceGrpc.RxProviderAccountServiceStub.class)
        .toInstance(
            (Rx3ProviderAccountServiceGrpc.RxProviderAccountServiceStub)
                extensionContext.getRoot().getStore(ExtensionContext.Namespace.GLOBAL).get("oam"));
  }
}
