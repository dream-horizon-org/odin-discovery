package com.dream11.odin.service;

import static com.dream11.odin.exception.Error.ACTION_NOT_SUPPORTED;
import static com.dream11.odin.exception.Error.RECORD_TYPE_VALIDATION;
import static com.dream11.odin.exception.Error.TTL_INVALID;

import com.dream11.odin.constant.ClientType;
import com.dream11.odin.constant.Constants;
import com.dream11.odin.constant.RecordType;
import com.dream11.odin.dao.ProviderDao;
import com.dream11.odin.dao.RecordDao;
import com.dream11.odin.dao.entity.ProviderEntity;
import com.dream11.odin.domain.Record;
import com.dream11.odin.domain.RecordDiff;
import com.dream11.odin.dto.BatchRequest;
import com.dream11.odin.dto.BatchResponse;
import com.dream11.odin.dto.RecordAction;
import com.dream11.odin.dto.RecordResponse;
import com.dream11.odin.dto.constants.Action;
import com.dream11.odin.dto.constants.DiscoveryProviderType;
import com.dream11.odin.dto.constants.Status;
import com.dream11.odin.exception.Error;
import com.dream11.odin.grpc.provideraccount.v1.GetAllProviderAccountsRequest;
import com.dream11.odin.grpc.provideraccount.v1.Rx3ProviderAccountServiceGrpc;
import com.dream11.odin.provider.DiscoveryProvider;
import com.dream11.odin.provider.DiscoveryProviderFactory;
import com.dream11.odin.util.ExceptionUtil;
import com.dream11.odin.util.OamUtil;
import com.google.inject.Inject;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.netty.util.internal.StringUtil;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RecordService {

  final Rx3ProviderAccountServiceGrpc.RxProviderAccountServiceStub providerAccountService;

  final RecordDao recordDao;
  final DiscoveryProviderFactory discoveryProviderFactory;

  final java.util.Map<
          com.dream11.odin.dao.entity.ProviderEntity, com.dream11.odin.provider.DiscoveryProvider>
      discoveryProviderCache = new java.util.HashMap<>();

  final ProviderDao providerDao;

  private static final ClientType CLIENT_TYPE = ClientType.CONTROLLER;

  private void validateBatchRequest(BatchRequest batchRequest) {
    if (StringUtil.isNullOrEmpty(batchRequest.getAccountName())) {
      throw ExceptionUtil.getException(Error.ACCOUNT_NAME_CANNOT_BE_EMPTY);
    }
    if (batchRequest.getRecordActions() == null) {
      throw ExceptionUtil.getException(Error.RECORD_ACTIONS_CANNOT_BE_EMPTY);
    }
    java.util.Set<String> uniqueIds = new java.util.HashSet<>();
    for (RecordAction action : batchRequest.getRecordActions()) {
      if (action.getDnsRecord() == null) {
        throw ExceptionUtil.getException(Error.RECORD_CANNOT_BE_EMPTY);
      }
      if (StringUtil.isNullOrEmpty(action.getDnsRecord().getName())) {
        throw ExceptionUtil.getException(Error.RECORD_NAME_CANNOT_BE_EMPTY);
      }
      if (!uniqueIds.add(action.getId())) {
        throw ExceptionUtil.getException(Error.DUPLICATE_ID_FOUND_IN_RECORD_ACTIONS);
      }
    }
  }

  public Single<BatchResponse> processBatch(Long orgId, BatchRequest batchRequest) {
    validateBatchRequest(batchRequest);

    Metadata metadata = new Metadata();
    metadata.put(
        Metadata.Key.of(Constants.ORG_ID_HEADER, Metadata.ASCII_STRING_MARSHALLER),
        String.valueOf(orgId));
    return providerAccountService
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
        .getAllProviderAccounts(
            GetAllProviderAccountsRequest.newBuilder().setFetchLinkedAccountDetails(true).build())
        .map(
            providerAccountResponse ->
                OamUtil.extractActiveDiscoveryProvider(providerAccountResponse, orgId))
        .flatMap(
            providerEntityMap -> {
              log.info("Processing providerEntityMap: {}", providerEntityMap);
              return Flowable.fromIterable(providerEntityMap.entrySet())
                  .flatMapSingle(
                      entry -> {
                        ProviderEntity providerEntity = entry.getValue();
                        return providerDao
                            .getProvider(
                                providerEntity.getOrgId(),
                                providerEntity.getAccount(),
                                providerEntity.getName(),
                                providerEntity.getConfig())
                            .doOnSuccess(
                                providerEntityFromDb ->
                                    providerEntityMap.put(entry.getKey(), providerEntityFromDb))
                            .switchIfEmpty(
                                Single.defer(
                                    () -> {
                                      log.info(
                                          "Provider not found, saving new provider: {}",
                                          providerEntity);
                                      providerDao
                                          .saveProvider(providerEntity)
                                          .doOnSuccess(
                                              savedProviderEntity ->
                                                  providerEntityMap.put(
                                                      entry.getKey(), savedProviderEntity));
                                      return Single.just(providerEntity);
                                    }));
                      })
                  .ignoreElements()
                  .andThen(Single.just(providerEntityMap));
            })
        // todo Handle case when provider changes - delete old provider and make new entry of
        // provider in case of provider
        .map(providerEntityMap -> processRecords(providerEntityMap, batchRequest))
        .map(Single::mergeDelayError)
        .flatMap(Flowable::toList)
        .map(responseList -> BatchResponse.builder().responseList(responseList).build())
        .doOnError(err -> log.error("Error {}", err.getMessage(), err));
  }

  private Optional<RecordResponse> validateRecordAction(
      RecordAction recordAction, RecordResponse.RecordResponseBuilder responseBuilder) {
    if (StringUtil.isNullOrEmpty(recordAction.getId())) {
      return Optional.of(
          responseBuilder
              .status(Status.FAILED)
              .message(Error.RECORD_ID_CANNOT_BE_EMPTY.getErrorMessage())
              .build());
    }
    if (recordAction.getAction() == null) {
      return Optional.of(
          responseBuilder
              .status(Status.FAILED)
              .message(Error.ACTION_REQUIRED_IN_RECORD_ACTION.getErrorMessage())
              .build());
    }
    if (recordAction.getAction().equals(Action.UPSERT)
        && (recordAction.getDnsRecord().getValues() == null
            || recordAction.getDnsRecord().getValues().isEmpty())) {
      return Optional.of(
          responseBuilder
              .status(Status.FAILED)
              .message(Error.RECORD_VALUE_CANNOT_BE_EMPTY.getErrorMessage())
              .build());
    }
    if (recordAction.getDnsRecord().getType() != null
        && !recordAction.getDnsRecord().getType().equalsIgnoreCase(RecordType.WEIGHTED.getName())
        && !recordAction.getDnsRecord().getType().equalsIgnoreCase(RecordType.SIMPLE.getName())
        && !recordAction.getDnsRecord().getType().isEmpty()) {
      return Optional.of(
          responseBuilder
              .status(Status.FAILED)
              .message(RECORD_TYPE_VALIDATION.getErrorMessage())
              .build());
    }
    if (recordAction.getDnsRecord().getTtlInSeconds() < 0) {
      return Optional.of(
          responseBuilder.status(Status.FAILED).message(TTL_INVALID.getErrorMessage()).build());
    }
    // Add weight validation for weighted records
    if (RecordType.WEIGHTED.getName().equalsIgnoreCase(recordAction.getDnsRecord().getType())
        && (recordAction.getDnsRecord().getWeight() < 0
            || recordAction.getDnsRecord().getWeight() > 100)) {
      return Optional.of(
          responseBuilder
              .status(Status.FAILED)
              .message(Error.WEIGHTED_RECORD_SHOULD_HAVE_WEIGHT.getErrorMessage())
              .build());
    }
    return Optional.empty();
  }

  private ProviderEntity findProviderEntity(
      RecordAction recordAction, Map<String, ProviderEntity> providerEntityMap) {
    return providerEntityMap.entrySet().stream()
        .filter(entry -> recordAction.getDnsRecord().getName().endsWith("." + entry.getKey()))
        .max(Comparator.comparingInt(entry -> entry.getKey().length()))
        .map(Map.Entry::getValue)
        .orElseThrow(() -> ExceptionUtil.getException(Error.NO_DISCOVERY_PROVIDER_FOUND));
  }

  private Single<RecordResponse> handleUpsertAction(
      RecordAction recordAction,
      ProviderEntity providerEntity,
      RecordResponse.RecordResponseBuilder responseBuilder,
      DiscoveryProvider discoveryProvider) {
    return recordDao
        .getRecord(
            recordAction.getDnsRecord().getName(),
            providerEntity.getId(),
            recordAction.getDnsRecord().getIdentifier())
        .flatMapSingle(
            dnsRecord -> {
              RecordDiff recordDiff = dnsRecord.getDiff(recordAction.getDnsRecord().getValues());
              if (dnsRecord.equalTo(recordAction.getDnsRecord())) {
                return Single.just(responseBuilder.status(Status.SUCCESSFUL).build());
              } else {
                if (dnsRecord.getType() != null
                    && !dnsRecord
                        .getType()
                        .equals(
                            RecordType.getValueDefaultWeighted(
                                recordAction.getDnsRecord().getType()))) {
                  return Single.just(
                      responseBuilder
                          .status(Status.FAILED)
                          .message(Error.RECORD_TYPE_CANNOT_CHANGE_ONCE_CREATED.getErrorMessage())
                          .build());
                }
                dnsRecord.setTtlInSeconds(recordAction.getDnsRecord().getTtlInSeconds());
                // change weight and identifier only for weighted records in database and provider
                // also update the dnsrecord in table for weight and identifier
                if (dnsRecord.getType() == RecordType.WEIGHTED) {
                  dnsRecord.setWeight(recordAction.getDnsRecord().getWeight());
                }
                return discoveryProvider
                    .updateRecord(dnsRecord, recordDiff)
                    .andThen(
                        recordDao
                            .updateRecord(
                                dnsRecord,
                                providerEntity.getId(),
                                recordDiff.getValuesToAdd(),
                                recordDiff.getValuesToDelete())
                            .andThen(Single.just(responseBuilder.status(Status.SUCCESSFUL).build()))
                            .onErrorReturn(
                                err ->
                                    responseBuilder
                                        .status(Status.FAILED)
                                        .message(err.getMessage())
                                        .build()));
              }
            })
        .switchIfEmpty(
            Single.defer(
                () -> {
                  com.dream11.odin.domain.Record domainRecord =
                      Record.create(recordAction.getDnsRecord(), CLIENT_TYPE);

                  return discoveryProvider
                      .createRecord(domainRecord)
                      .andThen(recordDao.saveRecord(domainRecord, providerEntity))
                      .andThen(Single.just(responseBuilder.status(Status.SUCCESSFUL).build()))
                      .onErrorReturn(
                          err ->
                              responseBuilder
                                  .status(Status.FAILED)
                                  .message(err.getMessage())
                                  .build());
                }));
  }

  private Single<RecordResponse> handleDeleteAction(
      RecordAction recordAction,
      ProviderEntity providerEntity,
      RecordResponse.RecordResponseBuilder responseBuilder,
      DiscoveryProvider discoveryProvider) {

    String recordName = recordAction.getDnsRecord().getName();
    Long providerId = providerEntity.getId();
    String identifier = recordAction.getDnsRecord().getIdentifier();
    return recordDao
        .getRecordsByName(recordName, providerId)
        .flatMapSingle(
            records -> {
              if (records.isEmpty()) {
                return successfulResponseWithLog(recordAction, responseBuilder);
              }
              if ((identifier == null || identifier.isEmpty()) && records.size() == 1) {
                // TODO to be revisited, this changes were done to handle case when identifier is
                // not provided and only one record is present then delete it if it matches by name,
                // this is done due to weighted being default so when weighted is created by default
                // then the identifier is set by discovery service , and then when delete is called
                // it is called without identifier
                log.info("found one record for delete action: {}", recordAction);
                return processSingleRecordDeletion(
                    records.get(0),
                    recordAction,
                    providerEntity,
                    responseBuilder,
                    discoveryProvider);
              }

              log.info("found more than one record for delete action: {}", recordAction);
              // processMultipleRecordDeletion also returns a Single.
              return processMultipleRecordDeletion(
                  recordAction, providerEntity, responseBuilder, discoveryProvider);
            })
        .switchIfEmpty(successfulResponseWithLog(recordAction, responseBuilder));
  }

  private Single<RecordResponse> processSingleRecordDeletion(
      Record record,
      RecordAction recordAction,
      ProviderEntity providerEntity,
      RecordResponse.RecordResponseBuilder responseBuilder,
      DiscoveryProvider discoveryProvider) {

    return recordDao
        .getRecord(record.getName(), providerEntity.getId(), record.getIdentifier())
        .flatMapSingle(
            dnsRecord -> {
              if (dnsRecord.checkIdentityIsEqualWithoutIdentifier(recordAction.getDnsRecord())) {
                return performDeletion(
                    dnsRecord, providerEntity, responseBuilder, discoveryProvider);
              }
              return partialMatchFailure(responseBuilder);
            })
        .switchIfEmpty(successfulResponseWithLog(recordAction, responseBuilder));
  }

  private Single<RecordResponse> processMultipleRecordDeletion(
      RecordAction recordAction,
      ProviderEntity providerEntity,
      RecordResponse.RecordResponseBuilder responseBuilder,
      DiscoveryProvider discoveryProvider) {
    return recordDao
        .getRecord(
            recordAction.getDnsRecord().getName(),
            providerEntity.getId(),
            recordAction.getDnsRecord().getIdentifier())
        .flatMapSingle(
            dnsRecord -> {
              if (dnsRecord.checkIdentityIsEqual(recordAction.getDnsRecord())) {
                return performDeletion(
                    dnsRecord, providerEntity, responseBuilder, discoveryProvider);
              }
              return partialMatchFailure(responseBuilder);
            })
        .switchIfEmpty(successfulResponseWithLog(recordAction, responseBuilder));
  }

  private Single<RecordResponse> performDeletion(
      Record dnsRecord,
      ProviderEntity providerEntity,
      RecordResponse.RecordResponseBuilder responseBuilder,
      DiscoveryProvider discoveryProvider) {

    return discoveryProvider
        .deleteRecord(dnsRecord)
        .andThen(
            recordDao.deleteRecord(
                dnsRecord.getName(), providerEntity.getId(), dnsRecord.getIdentifier()))
        .andThen(Single.just(responseBuilder.status(Status.SUCCESSFUL).build()))
        .onErrorReturn(
            err -> responseBuilder.status(Status.FAILED).message(err.getMessage()).build());
  }

  private Single<RecordResponse> partialMatchFailure(
      RecordResponse.RecordResponseBuilder responseBuilder) {
    return Single.just(
        responseBuilder
            .status(Status.FAILED)
            .message(Error.PARTIAL_MATCHING_RECORD.getErrorMessage())
            .build());
  }

  private Single<RecordResponse> successfulResponseWithLog(
      RecordAction recordAction, RecordResponse.RecordResponseBuilder responseBuilder) {

    return Single.defer(
        () -> {
          log.warn(
              "Record not found for record action name: {}, dnsRecord: {}",
              recordAction.getAction().name(),
              recordAction.getDnsRecord().getName());

          return Single.just(responseBuilder.status(Status.SUCCESSFUL).build());
        });
  }

  Single<RecordResponse> processRecordAction(
      RecordAction recordAction, Map<String, ProviderEntity> providerEntityMap) {
    RecordResponse.RecordResponseBuilder responseBuilder =
        RecordResponse.builder().id(recordAction.getId());
    ProviderEntity providerEntity;
    try {
      providerEntity = findProviderEntity(recordAction, providerEntityMap);
    } catch (Exception e) {
      return Single.just(responseBuilder.status(Status.FAILED).message(e.getMessage()).build());
    }

    DiscoveryProvider discoveryProvider =
        discoveryProviderCache.computeIfAbsent(
            providerEntity,
            a ->
                discoveryProviderFactory.createDiscoveryProvider(
                    DiscoveryProviderType.customValueOf(a.getName()),
                    new JsonObject(a.getConfig())));

    Optional<RecordResponse> validationResult = validateRecordAction(recordAction, responseBuilder);
    if (validationResult.isPresent()) {
      return Single.just(validationResult.get());
    }

    switch (recordAction.getAction()) {
      case UPSERT -> {
        return handleUpsertAction(recordAction, providerEntity, responseBuilder, discoveryProvider);
      }
      case DELETE -> {
        return handleDeleteAction(recordAction, providerEntity, responseBuilder, discoveryProvider);
      }
      default -> throw ExceptionUtil.getException(
          ACTION_NOT_SUPPORTED, recordAction.getAction().name());
    }
  }

  private BatchRequest removeDuplicateRecords(BatchRequest batchRequest) {
    return BatchRequest.builder()
        .accountName(batchRequest.getAccountName())
        .recordActions(
            batchRequest.getRecordActions().stream()
                .collect(
                    Collectors.toMap(
                        recordAction -> recordAction.getDnsRecord().getName(),
                        Function.identity(),
                        (existing, replacement) -> replacement))
                .values()
                .stream()
                .toList())
        .build();
  }

  private List<Single<RecordResponse>> processRecords(
      Map<String, ProviderEntity> providerEntityMap, BatchRequest batchRequest) {

    List<Single<RecordResponse>> list = new ArrayList<>();
    removeDuplicateRecords(batchRequest)
        .getRecordActions()
        .forEach(recordAction -> list.add(processRecordAction(recordAction, providerEntityMap)));
    return list;
  }
}
