package com.dream11.odin.client.impl;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.util.ConfigUtil;
import com.dream11.odin.util.JsonUtil;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.rxjava3.config.ConfigRetriever;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.sqlclient.PoolOptions;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MysqlClientImpl implements MysqlClient {

  final Vertx vertx;
  MySQLPool masterClient;
  MySQLPool slaveClient;
  JsonObject config;

  @Override
  public Completable rxConnect() {

    ConfigRetriever configRetriever = ConfigUtil.getRetriever(this.vertx);
    return configRetriever
        .rxGetConfig()
        .doOnSuccess(
            conf -> {
              log.info("Received mysql config: {}", conf);
              this.config = conf;
              this.createMasterSlavePool();
            })
        .ignoreElement();
  }

  @Override
  public Completable rxClose() {
    return this.masterClient.rxClose().andThen(this.slaveClient.rxClose());
  }

  private MySQLConnectOptions createConnectOptions(String flattenedKey) {
    return new MySQLConnectOptions(JsonUtil.getJsonObjectFromNestedJson(this.config, flattenedKey));
  }

  private PoolOptions createPoolOptions(String flattenedKey) {
    return new PoolOptions(JsonUtil.getJsonObjectFromNestedJson(this.config, flattenedKey));
  }

  private void createMasterSlavePool() {
    MySQLConnectOptions masterConnectOptions = createConnectOptions("mysql.master.connectOptions");
    MySQLConnectOptions slaveConnectOptions = createConnectOptions("mysql.slave.connectOptions");
    PoolOptions masterPoolOptions = createPoolOptions("mysql.master.poolOptions");
    PoolOptions slavePoolOptions = createPoolOptions("mysql.slave.poolOptions");
    this.masterClient = MySQLPool.pool(this.vertx, masterConnectOptions, masterPoolOptions);
    this.slaveClient = MySQLPool.pool(this.vertx, slaveConnectOptions, slavePoolOptions);
  }
}
