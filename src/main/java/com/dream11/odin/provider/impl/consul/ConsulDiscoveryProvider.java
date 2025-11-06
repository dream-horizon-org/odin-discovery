package com.dream11.odin.provider.impl.consul;

import com.dream11.odin.domain.Record;
import com.dream11.odin.domain.RecordDiff;
import com.dream11.odin.provider.DiscoveryProvider;
import com.dream11.odin.provider.impl.consul.dto.ConsulRequest;
import com.dream11.odin.provider.impl.consul.dto.NodeMeta;
import com.dream11.odin.provider.impl.consul.dto.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.rxjava3.ext.web.client.predicate.ResponsePredicate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ConsulDiscoveryProvider implements DiscoveryProvider {

  public static final String CONFIG_HOST = "host";

  public static final String CONFIG_PORT = "port";
  private static final String REGISTER_ENDPOINT = "/v1/catalog/register";
  private static final String DEREGISTER_ENDPOINT = "/v1/catalog/deregister";
  private static final String DESCRIBE_SERVICE_ENDPOINT = "/v1/catalog/service/";

  private static final String EXTERNAL_NODE = "true";

  private static final String EXTERNAL_PROBE = "true";

  public static final String TIMEOUT_SECS = "timeoutInSecs";

  final WebClient webClient;
  final String host;

  final int port;

  final int timeoutInSecs;

  final ObjectMapper objectMapper;
  final List<String> tags;

  final JsonObject config;

  @Override
  @SneakyThrows
  public Completable createRecord(Record dnsRecord) {
    List<Completable> completables =
        dnsRecord.getValues().stream()
            .map(value -> createNode(dnsRecord.getName(), value))
            .toList();
    return Completable.mergeDelayError(completables);
  }

  @Override
  public Completable updateRecord(Record dnsRecord, RecordDiff recordDiff) {
    return this.appendValuesToRecord(dnsRecord, recordDiff.getValuesToAdd())
        .andThen(this.deleteValuesFromRecord(dnsRecord, recordDiff.getValuesToDelete()));
  }

  @SneakyThrows
  private Completable createNode(String recordName, String value) {
    return this.webClient
        .put(port, host, REGISTER_ENDPOINT)
        .expect(ResponsePredicate.SC_SUCCESS)
        .timeout(timeoutInSecs * 1000L)
        .rxSendJson(
            ConsulRequest.builder()
                .node(buildNodeId(recordName, value))
                .address(value)
                .service(
                    Service.builder()
                        .tags(tags)
                        .discoveryService(updateRecordName(recordName))
                        .build())
                .nodeMeta(
                    NodeMeta.builder()
                        .externalNode(EXTERNAL_NODE)
                        .externalProbe(EXTERNAL_PROBE)
                        .build())
                .build())
        .doOnError(
            throwable ->
                log.error(
                    "Error while creating node for record {} and value {} : {}",
                    recordName,
                    value,
                    throwable.getMessage()))
        .ignoreElement();
  }

  public Completable appendValuesToRecord(Record dnsRecord, List<String> values) {
    return Completable.mergeDelayError(
        values.stream().map(value -> createNode(dnsRecord.getName(), value)).toList());
  }

  @Override
  public Completable deleteRecord(Record dnsRecord) {
    return getServiceDetails(dnsRecord.getName())
        .map(
            serviceDetails ->
                IntStream.range(0, serviceDetails.size())
                    .mapToObj(
                        index -> deleteNode(serviceDetails.getJsonObject(index).getString("Node")))
                    .toList())
        .flatMapCompletable(Completable::mergeDelayError);
  }

  public Completable deleteValuesFromRecord(Record dnsRecord, List<String> values) {

    List<Completable> completables =
        values.stream().map(value -> deleteNode(buildNodeId(dnsRecord.getName(), value))).toList();
    return Completable.mergeDelayError(completables);
  }

  private String buildNodeId(String recordName, String value) {
    return updateRecordName(recordName) + "-node-" + value;
  }

  private Completable deleteNode(String nodeId) {
    return this.webClient
        .put(port, host, DEREGISTER_ENDPOINT)
        .expect(ResponsePredicate.SC_SUCCESS)
        .rxSendJsonObject(new JsonObject().put("Node", nodeId))
        .doOnError(
            throwable ->
                log.error("Error while deleting node {} : {}", nodeId, throwable.getMessage()))
        .ignoreElement();
  }

  private Single<JsonArray> getServiceDetails(String serviceName) {

    return this.webClient
        .get(port, host, DESCRIBE_SERVICE_ENDPOINT + updateRecordName(serviceName))
        .expect(ResponsePredicate.SC_SUCCESS)
        .rxSend()
        .map(response -> new JsonArray(response.bodyAsString()))
        .doOnError(
            throwable ->
                log.error(
                    "Error while fetching service details for service {} : {}",
                    serviceName,
                    throwable.getMessage()));
  }

  private String updateRecordName(String recordName) {
    return config.getJsonArray("domains").stream()
        .map(JsonObject.class::cast)
        .filter(
            domainObject ->
                recordName.endsWith(domainObject.getString("name", "NA_INVALID_DOMAIN")))
        .max(
            Comparator.comparingInt(
                domainObject -> domainObject.getString("name", "NA_INVALID_DOMAIN").length()))
        .map(
            domainObject ->
                recordName.substring(
                    0, recordName.length() - domainObject.getString("name").length() - 1))
        .orElseThrow(() -> new IllegalArgumentException("Invalid domain name: " + recordName));
  }
}
