package com.dream11.odin.service;

import com.dream11.odin.dao.HealthCheckDao;
import com.dream11.odin.util.JsonUtil;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class HealthcheckService {

  final HealthCheckDao healthCheckDao;

  public Single<JsonObject> healthcheck() {
    return Single.zipArray(JsonUtil::jsonMerge, healthCheckDao.mysqlHealthCheck());
  }
}
