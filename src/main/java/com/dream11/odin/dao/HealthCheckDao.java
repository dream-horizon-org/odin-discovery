package com.dream11.odin.dao;

import com.dream11.odin.client.MysqlClient;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class HealthCheckDao {
  final MysqlClient mysqlClient;

  public Single<JsonObject> mysqlHealthCheck() {
    return mysqlClient
        .getMasterClient()
        .query("SELECT 1;")
        .rxExecute()
        .map(rowSet -> new JsonObject().put("mysql", "UP"));
  }
}
