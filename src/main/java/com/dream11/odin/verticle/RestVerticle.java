package com.dream11.odin.verticle;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.client.impl.MysqlClientImpl;
import com.dream11.odin.config.ApplicationConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.grpc.provideraccount.v1.Rx3ProviderAccountServiceGrpc;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.ContextUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.dream11.rest.AbstractRestVerticle;
import com.dream11.rest.ClassInjector;
import io.grpc.ChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RestVerticle extends AbstractRestVerticle {

  MysqlClient mysqlClient;

  ManagedChannel odinAccountManagerChannel;

  WebClient webClient;

  public RestVerticle() {
    super(
        "com.dream11.odin",
        new HttpServerOptions()
            .setPort(SharedDataUtil.getInstance(ApplicationConfig.class).getPort()));
  }

  @SneakyThrows
  @Override
  public Completable rxStart() {

    return createClients().andThen(this.mysqlClient.rxConnect()).andThen(super.rxStart());
  }

  private Completable createClients() {

    ApplicationConfig applicationConfig = this.getInjector().getInstance(ApplicationConfig.class);
    this.mysqlClient = new MysqlClientImpl(this.vertx);
    ContextUtil.setInstance(this.mysqlClient, Constants.MYSQL_CLIENT);
    ChannelCredentials credentials =
        TlsChannelCredentials.newBuilder()
            .trustManager(InsecureTrustManagerFactory.INSTANCE.getTrustManagers())
            .build();
    this.odinAccountManagerChannel =
        NettyChannelBuilder.forAddress(
                applicationConfig.getOdinAccountManagerConfig().getHost(),
                applicationConfig.getOdinAccountManagerConfig().getPort(),
                credentials)
            .maxInboundMessageSize(10 * 1024 * 1024)
            .keepAliveTimeout(30L, TimeUnit.SECONDS)
            .build();
    this.webClient = WebClient.create(this.vertx);
    ContextUtil.setInstance(webClient, Constants.WEB_CLIENT);
    Rx3ProviderAccountServiceGrpc.RxProviderAccountServiceStub providerAccountService =
        Rx3ProviderAccountServiceGrpc.newRxStub(odinAccountManagerChannel);
    ContextUtil.setInstance(providerAccountService, Constants.PROVIDER_ACCOUNT_SERVICE);
    ContextUtil.setInstance(applicationConfig, Constants.APP_CONFIG);
    return Completable.complete();
  }

  @Override
  protected ClassInjector getInjector() {
    return SharedDataUtil.getInstance(this.vertx.getDelegate(), GuiceInjector.class);
  }

  @Override
  public Completable rxStop() {
    return this.mysqlClient
        .rxClose()
        .andThen(Completable.fromAction(() -> odinAccountManagerChannel.shutdown()))
        .andThen(Completable.fromAction(() -> webClient.close()))
        .andThen(super.rxStop());
  }
}
