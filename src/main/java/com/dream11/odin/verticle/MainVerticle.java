package com.dream11.odin.verticle;

import com.dream11.odin.Deployable;
import com.dream11.odin.config.ApplicationConfig;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.injector.GuiceInjector;
import com.dream11.odin.util.ConfigUtil;
import com.dream11.odin.util.SharedDataUtil;
import com.dream11.rest.ClassInjector;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.impl.cpu.CpuCoreSensor;
import io.vertx.rxjava3.core.AbstractVerticle;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainVerticle extends AbstractVerticle {

  @Override
  public Completable rxStart() {
    List<Deployable> deployables =
        List.of(
            new Deployable(
                Constants.REST_VERTICLE,
                new DeploymentOptions().setInstances(this.getNumOfCores())));

    return this.readConfig()
        .ignoreElement()
        .andThen(Observable.fromIterable(deployables))
        .flatMapSingle(
            deployable ->
                vertx.rxDeployVerticle(deployable.getVerticle(), deployable.getDeploymentOptions()))
        .toList()
        .ignoreElement()
        .doOnError(error -> log.error("Failed to deploy verticles", error))
        .doOnComplete(() -> log.info("Deployed all verticles!. Started Application"));
  }

  private Integer getNumOfCores() {
    return CpuCoreSensor.availableProcessors();
  }

  private Single<ApplicationConfig> readConfig() {

    ObjectMapper objectMapper = getInjector().getInstance(ObjectMapper.class);
    return ConfigUtil.getRetriever(vertx)
        .rxGetConfig()
        .map(config -> objectMapper.readValue(config.encode(), ApplicationConfig.class))
        .doOnSuccess(config -> SharedDataUtil.setInstance(this.vertx.getDelegate(), config))
        .doOnSuccess(ApplicationConfig::validate);
  }

  protected ClassInjector getInjector() {
    return SharedDataUtil.getInstance(this.vertx.getDelegate(), GuiceInjector.class);
  }
}
