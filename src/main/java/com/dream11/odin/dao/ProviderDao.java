package com.dream11.odin.dao;

import static com.dream11.odin.dao.query.MysqlQuery.CREATE_PROVIDER;
import static com.dream11.odin.util.JsonUtil.sortJsonObject;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.dao.entity.ProviderEntity;
import com.dream11.odin.dao.query.MysqlQuery;
import com.dream11.odin.exception.Error;
import com.dream11.odin.util.ExceptionUtil;
import com.dream11.odin.util.MaybeUtil;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProviderDao {

  final MysqlClient mysqlClient;

  public Maybe<ProviderEntity> getProvider(
      Long orgId, String accountName, String providerName, String config) {

    return mysqlClient
        .getSlaveClient()
        .preparedQuery(MysqlQuery.GET_PROVIDER)
        .rxExecute(Tuple.of(providerName, orgId, accountName, config))
        .filter(rowSet -> rowSet.size() > 0)
        .map(this::buildProviderEntity)
        .compose(MaybeUtil.applyDebugLogs(log));
  }

  private ProviderEntity buildProviderEntity(RowSet<Row> rowSet) {
    Row row = rowSet.iterator().next();
    return ProviderEntity.builder()
        .id(row.getLong("id"))
        .name(row.getString("name"))
        .account(row.getString("account_name"))
        .orgId(row.getLong("org_id"))
        .config(row.getString("config"))
        .configHash(row.getString("config_hash"))
        .build();
  }

  public Single<ProviderEntity> saveProvider(ProviderEntity providerEntity) {

    Object[] params = {
      providerEntity.getOrgId(),
      providerEntity.getAccount(),
      providerEntity.getName(),
      providerEntity.getConfig(),
      DigestUtils.sha256Hex(sortJsonObject(new JsonObject(providerEntity.getConfig())).toString())
    };

    return mysqlClient
        .getMasterClient()
        .preparedQuery(CREATE_PROVIDER)
        .rxExecute(Tuple.wrap(params))
        .map(
            insertResult -> {
              if (insertResult.rowCount() == 0) {
                log.error("Insert failed for provider {}", providerEntity);
                throw ExceptionUtil.getException(Error.INTERNAL_SERVER_ERROR);
              }

              return insertResult;
            })
        .map(
            rowSet ->
                ProviderEntity.builder()
                    .id(rowSet.property(MySQLClient.LAST_INSERTED_ID))
                    .name(providerEntity.getName())
                    .account(providerEntity.getAccount())
                    .orgId(providerEntity.getOrgId())
                    .config(providerEntity.getConfig())
                    .configHash(providerEntity.getConfigHash())
                    .build())
        .compose(SingleUtil.applyDebugLogs(log));
  }
}
