package com.dream11.odin.dao;

import static com.dream11.odin.dao.query.MysqlQuery.CREATE_RECORD;
import static com.dream11.odin.dao.query.MysqlQuery.CREATE_RECORD_DESTINATION;
import static com.dream11.odin.dao.query.MysqlQuery.DELETE_RECORD;
import static com.dream11.odin.dao.query.MysqlQuery.DELETE_RECORD_DESTINATION;
import static com.dream11.odin.dao.query.MysqlQuery.DELETE_RECORD_DESTINATION_BY_VALUE;

import com.dream11.odin.client.MysqlClient;
import com.dream11.odin.constant.ClientType;
import com.dream11.odin.constant.RecordType;
import com.dream11.odin.dao.entity.ProviderEntity;
import com.dream11.odin.dao.entity.RecordDestinationEntity;
import com.dream11.odin.dao.entity.RecordEntity;
import com.dream11.odin.dao.query.MysqlQuery;
import com.dream11.odin.domain.Record;
import com.dream11.odin.exception.Error;
import com.dream11.odin.util.ExceptionUtil;
import com.dream11.odin.util.MaybeUtil;
import com.dream11.odin.util.SingleUtil;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RecordDao {

  final MysqlClient mysqlClient;

  public Maybe<List<Record>> getRecordsByName(String recordName, long providerId) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(MysqlQuery.GET_RECORD)
        .rxExecute(Tuple.of(recordName, providerId))
        .filter(rowSet -> rowSet.size() > 0)
        .map(this::buildRecords)
        .compose(MaybeUtil.applyDebugLogs(log));
  }

  public Maybe<Record> getRecord(String recordName, long providerId, String identifier) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(MysqlQuery.GET_RECORD_WITH_IDENTIFIER)
        .rxExecute(Tuple.of(recordName, providerId, identifier))
        .filter(rowSet -> rowSet.size() > 0)
        .map(this::buildRecord)
        .compose(MaybeUtil.applyDebugLogs(log));
  }

  private Maybe<RecordEntity> getRecordEntity(
      String recordName, long providerId, String identifier) {
    return mysqlClient
        .getSlaveClient()
        .preparedQuery(MysqlQuery.GET_RECORD_WITH_IDENTIFIER)
        .rxExecute(Tuple.of(recordName, providerId, identifier))
        .filter(rowSet -> rowSet.size() > 0)
        .map(this::buildRecordEntity)
        .compose(MaybeUtil.applyDebugLogs(log));
  }

  private Completable deleteDanglingRecord(SqlConnection sqlConnection, long recordId) {
    return sqlConnection
        .preparedQuery(MysqlQuery.DELETE_DANGLING_RECORD)
        .rxExecute(Tuple.of(recordId))
        .compose(SingleUtil.applyDebugLogs(log))
        .ignoreElement();
  }

  private Record buildRecord(RowSet<Row> rowSet) {
    List<String> values = new ArrayList<>();
    rowSet.forEach(row -> values.add(row.getString("destination")));
    Row row = rowSet.iterator().next();
    return Record.builder()
        .ttlInSeconds(row.getInteger("ttl_in_seconds"))
        .weight(row.getInteger("weight"))
        .clientType(ClientType.valueOf(row.getString("client_type")))
        .name(row.getString("name"))
        .values(values)
        .type(RecordType.getValueDefaultWeighted(row.getString("type")))
        .identifier(row.getString("identifier"))
        .build();
  }

  private List<Record> buildRecords(RowSet<Row> rowSet) {
    List<Record> records = new ArrayList<>();
    rowSet.forEach(
        row ->
            records.add(
                Record.builder()
                    .ttlInSeconds(row.getInteger("ttl_in_seconds"))
                    .weight(row.getInteger("weight"))
                    .clientType(ClientType.valueOf(row.getString("client_type")))
                    .name(row.getString("name"))
                    .values(new ArrayList<>())
                    .type(RecordType.getValueDefaultWeighted(row.getString("type")))
                    .identifier(row.getString("identifier"))
                    .build()));
    return records;
  }

  private RecordEntity buildRecordEntity(RowSet<Row> rowSet) {
    Row row = rowSet.iterator().next();
    return RecordEntity.builder().id(row.getLong("id")).name(row.getString("name")).build();
  }

  public Completable saveRecord(Record dnsRecord, ProviderEntity providerEntity) {

    return mysqlClient
        .getMasterClient()
        .rxWithTransaction(
            connection ->
                createRecord(connection, dnsRecord, providerEntity)
                    .map(recordEntity -> buildRecordDestinations(dnsRecord, recordEntity))
                    .flatMapCompletable(
                        recordDestinationEntities ->
                            createRecordDestinations(connection, recordDestinationEntities))
                    .toMaybe())
        .ignoreElement();
  }

  public Completable deleteRecord(String recordName, long providerId, String identifier) {

    return getRecordEntity(recordName, providerId, identifier)
        .flatMapCompletable(
            dnsRecord ->
                mysqlClient
                    .getMasterClient()
                    .rxWithTransaction(
                        connection ->
                            deleteRecordDestinations(connection, dnsRecord.id())
                                .andThen(deleteRecord(connection, dnsRecord.id()))
                                .toMaybe())
                    .ignoreElement());
  }

  public Completable deleteRecordWithCompletableTransaction(
      String recordName,
      String value,
      long providerId,
      String recordIdentifier,
      Completable completable) {

    return getRecordEntity(recordName, providerId, recordIdentifier)
        .flatMapCompletable(
            dnsRecord ->
                mysqlClient
                    .getMasterClient()
                    .rxWithTransaction(
                        connection ->
                            deleteRecordDestinations(connection, List.of(value), dnsRecord.id())
                                .andThen(deleteDanglingRecord(connection, dnsRecord.id()))
                                .andThen(completable)
                                .toMaybe())
                    .ignoreElement());
  }

  private Completable deleteRecordDestinations(SqlConnection sqlConnection, long recordId) {

    return sqlConnection
        .preparedQuery(DELETE_RECORD_DESTINATION)
        .rxExecute(Tuple.of(recordId))
        .compose(SingleUtil.applyDebugLogs(log))
        .ignoreElement();
  }

  private Completable deleteRecordDestinations(
      SqlConnection sqlConnection, List<String> values, long recordId) {

    if (values.isEmpty()) {
      return Completable.complete();
    }
    List<Tuple> tuples = values.stream().map(value -> Tuple.of(recordId, value)).toList();
    return sqlConnection
        .preparedQuery(DELETE_RECORD_DESTINATION_BY_VALUE)
        .rxExecuteBatch(tuples)
        .compose(SingleUtil.applyDebugLogs(log))
        .ignoreElement();
  }

  private Completable deleteRecord(SqlConnection sqlConnection, long recordId) {

    return sqlConnection
        .preparedQuery(DELETE_RECORD)
        .rxExecute(Tuple.of(recordId))
        .map(
            deleteResult -> {
              if (deleteResult.rowCount() == 0) {
                log.error("Delete failed for record {}", recordId);
                throw ExceptionUtil.getException(Error.INTERNAL_SERVER_ERROR);
              }
              return deleteResult;
            })
        .compose(SingleUtil.applyDebugLogs(log))
        .ignoreElement();
  }

  private List<RecordDestinationEntity> buildRecordDestinations(
      Record dnsRecord, RecordEntity recordEntity) {

    return dnsRecord.getValues().stream()
        .map(
            destination ->
                RecordDestinationEntity.builder()
                    .recordId(recordEntity.id())
                    .destination(destination)
                    .build())
        .toList();
  }

  public Completable updateRecordTtlWeights(
      SqlConnection connection, RecordEntity dnsRecord, Record modifiedDnsRecord) {
    return connection
        .preparedQuery(MysqlQuery.UPDATE_RECORD)
        .rxExecute(
            Tuple.of(
                modifiedDnsRecord.getTtlInSeconds(), modifiedDnsRecord.getWeight(), dnsRecord.id()))
        .onErrorReturn(
            err -> {
              log.error(
                  "Error while updating record {}: {}",
                  modifiedDnsRecord.getName(),
                  err.getMessage());
              throw ExceptionUtil.getException(Error.UPDATE_RECORD_FAILED);
            })
        .map(
            updateResult -> {
              if (updateResult.rowCount() == 0) {
                log.error("Update failed for record {}", modifiedDnsRecord.getName());
                throw ExceptionUtil.getException(Error.UPDATE_RECORD_FAILED);
              }
              return updateResult;
            })
        .compose(SingleUtil.applyDebugLogs(log))
        .ignoreElement();
  }

  public Completable createRecordDestinations(
      SqlConnection sqlConnection, List<RecordDestinationEntity> recordDestinationEntities) {

    if (recordDestinationEntities.isEmpty()) {
      return Completable.complete();
    }
    List<Tuple> tuples =
        recordDestinationEntities.stream()
            .map(
                recordDestinationEntity ->
                    Tuple.of(
                        recordDestinationEntity.recordId(), recordDestinationEntity.destination()))
            .toList();
    return sqlConnection
        .preparedQuery(CREATE_RECORD_DESTINATION)
        .rxExecuteBatch(tuples)
        .map(
            insertResult -> {
              if (insertResult.rowCount() != 1) {
                log.error("Insert failed for recordDestinations {}", recordDestinationEntities);
                throw ExceptionUtil.getException(Error.INTERNAL_SERVER_ERROR);
              }
              return insertResult;
            })
        .compose(SingleUtil.applyDebugLogs(log))
        .ignoreElement();
  }

  private Single<RecordEntity> createRecord(
      SqlConnection sqlConnection, Record dnsRecord, ProviderEntity providerEntity) {
    Object[] params = {
      dnsRecord.getName(),
      dnsRecord.getTtlInSeconds(),
      dnsRecord.getWeight(),
      dnsRecord.getClientType(),
      providerEntity.getId(),
      dnsRecord.getType().getName(),
      dnsRecord.getIdentifier()
    };
    return sqlConnection
        .preparedQuery(CREATE_RECORD)
        .rxExecute(Tuple.wrap(params))
        .map(
            insertResult -> {
              if (insertResult.rowCount() == 0) {
                log.error("Insert failed for record {}", dnsRecord.getName());
                throw ExceptionUtil.getException(Error.INTERNAL_SERVER_ERROR);
              }
              return insertResult;
            })
        .map(
            rowSet ->
                new RecordEntity(
                    rowSet.property(MySQLClient.LAST_INSERTED_ID),
                    dnsRecord.getName(),
                    providerEntity.getId(),
                    dnsRecord.getTtlInSeconds(),
                    dnsRecord.getWeight(),
                    dnsRecord.getIdentifier(),
                    dnsRecord.getClientType()));
  }

  public Completable updateRecord(
      Record modifiedDnsRecord,
      long providerId,
      List<String> valuesToAdd,
      List<String> valuesToRemove) {
    return getRecordEntity(
            modifiedDnsRecord.getName(), providerId, modifiedDnsRecord.getIdentifier())
        .flatMapCompletable(
            dnsRecord ->
                mysqlClient
                    .getMasterClient()
                    .rxWithTransaction(
                        connection -> {
                          List<RecordDestinationEntity> destinationsToBeAdded = new ArrayList<>();
                          valuesToAdd.forEach(
                              value ->
                                  destinationsToBeAdded.add(
                                      RecordDestinationEntity.builder()
                                          .recordId(dnsRecord.id())
                                          .destination(value)
                                          .build()));
                          // update ttl and weight
                          if (modifiedDnsRecord.getTtlInSeconds() != dnsRecord.ttlInSeconds()
                              || modifiedDnsRecord.getWeight() != dnsRecord.weight()) {
                            return updateRecordTtlWeights(connection, dnsRecord, modifiedDnsRecord)
                                .andThen(
                                    createRecordDestinations(connection, destinationsToBeAdded)
                                        .andThen(
                                            deleteRecordDestinations(
                                                connection, valuesToRemove, dnsRecord.id()))
                                        .toMaybe());
                          }
                          return createRecordDestinations(connection, destinationsToBeAdded)
                              .andThen(
                                  deleteRecordDestinations(
                                      connection, valuesToRemove, dnsRecord.id()))
                              .toMaybe();
                        })
                    .ignoreElement());
  }
}
