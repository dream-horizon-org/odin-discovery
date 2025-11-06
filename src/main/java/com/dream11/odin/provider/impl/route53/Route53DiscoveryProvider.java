package com.dream11.odin.provider.impl.route53;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.retry.RetryMode;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.AssumeRoleResult;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.dream11.odin.config.ApplicationConfig;
import com.dream11.odin.constant.RecordType;
import com.dream11.odin.domain.Record;
import com.dream11.odin.domain.RecordDiff;
import com.dream11.odin.exception.Error;
import com.dream11.odin.provider.DiscoveryProvider;
import com.dream11.odin.util.ExceptionUtil;
import com.dream11.odin.util.IPUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.internal.StringUtil;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class Route53DiscoveryProvider implements DiscoveryProvider {
  final WebClient webClient;
  final ObjectMapper objectMapper;
  // Expose read-only access to provider configuration (domains, region, etc.).
  @Getter final JsonObject config;
  final ApplicationConfig applicationConfig;

  public AmazonRoute53 createRoute53Client() {
    String roleArn = config.getString("assumeRoleARN");
    String assumeRegion = config.getString("region");
    String awsSTSEndpoint = applicationConfig.getAwsSTSEndpoint();
    String port = applicationConfig.getAwsEndpointPort();
    String awsRoute53Endpoint = applicationConfig.getAwsRoute53Endpoint();
    String awsRegion = applicationConfig.getAwsRegion();
    if (port != null && !port.isEmpty()) {
      awsSTSEndpoint += ":" + port;
      awsRoute53Endpoint += ":" + port;
    }
    log.info("awsSTSEndpoint :" + awsSTSEndpoint);
    ClientConfiguration clientConfig = new ClientConfiguration();
    clientConfig.setMaxErrorRetry(applicationConfig.getAwsRetryCount());
    clientConfig.setRetryMode(RetryMode.STANDARD);

    AWSSecurityTokenServiceClientBuilder stsClientBuilder =
        AWSSecurityTokenServiceClientBuilder.standard();
    stsClientBuilder
        .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(awsSTSEndpoint, awsRegion))
        .withClientConfiguration(clientConfig);

    AWSSecurityTokenService stsClient = stsClientBuilder.build();
    AssumeRoleRequest assumeRoleRequest =
        new AssumeRoleRequest().withRoleArn(roleArn).withRoleSessionName("session");
    AssumeRoleResult assumeRoleResult = stsClient.assumeRole(assumeRoleRequest);
    Credentials sessionCredentials = assumeRoleResult.getCredentials();

    BasicSessionCredentials awsCredentials =
        new BasicSessionCredentials(
            sessionCredentials.getAccessKeyId(),
            sessionCredentials.getSecretAccessKey(),
            sessionCredentials.getSessionToken());
    return AmazonRoute53ClientBuilder.standard()
        .withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(awsRoute53Endpoint, assumeRegion))
        .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
        .withClientConfiguration(clientConfig)
        .build();
  }

  private ChangeResourceRecordSetsRequest prepareRecordRequest(
      Record dnsRecord, ChangeAction action) {
    RRType rrType = getRrType(dnsRecord);
    List<ResourceRecord> resourceRecords =
        dnsRecord.getValues().stream().map(value -> new ResourceRecord().withValue(value)).toList();
    String hostedZoneId = getHostedZoneId(dnsRecord);
    ChangeResourceRecordSetsRequest request =
        new ChangeResourceRecordSetsRequest().withHostedZoneId(hostedZoneId);

    if (RecordType.WEIGHTED.getName().equalsIgnoreCase(dnsRecord.getType().getName())) {
      if (StringUtil.isNullOrEmpty(dnsRecord.getIdentifier())) {
        throw ExceptionUtil.getException(Error.IDENTIFIER_IS_REQUIRED_FOR_WEIGHTED_RECORD);
      }
      request.withChangeBatch(
          new ChangeBatch()
              .withChanges(
                  new Change()
                      .withAction(action)
                      .withResourceRecordSet(
                          new ResourceRecordSet()
                              .withName(dnsRecord.getName())
                              .withType(rrType)
                              .withWeight(dnsRecord.getWeight())
                              .withTTL(dnsRecord.getTtlInSeconds())
                              .withSetIdentifier(dnsRecord.getIdentifier())
                              .withResourceRecords(resourceRecords))));
    } else {
      request.withChangeBatch(
          new ChangeBatch()
              .withChanges(
                  new Change()
                      .withAction(action)
                      .withResourceRecordSet(
                          new ResourceRecordSet()
                              .withName(dnsRecord.getName())
                              .withType(rrType)
                              .withTTL(dnsRecord.getTtlInSeconds())
                              .withResourceRecords(resourceRecords))));
    }
    return request;
  }

  private Completable operateRecordEntry(Record dnsRecord, ChangeAction action) {
    ChangeResourceRecordSetsRequest request;
    try {
      request = prepareRecordRequest(dnsRecord, action);
    } catch (Exception e) {
      return Completable.fromAction(
          () -> {
            throw e;
          });
    }
    return Completable.fromAction(() -> createRoute53Client().changeResourceRecordSets(request));
  }

  private static RRType getRrType(Record dnsRecord) {
    RRType rrType = RRType.CNAME;
    if (IPUtil.isIpAddress(dnsRecord.getValues())) {
      rrType = RRType.A;
    }
    return rrType;
  }

  private String getHostedZoneId(Record dnsRecord) {
    return config.getJsonArray("domains").stream()
        .map(JsonObject.class::cast)
        .filter(
            domainObject ->
                dnsRecord.getName().endsWith(domainObject.getString("name", "NA_INVALID_DOMAIN")))
        .max(
            Comparator.comparingInt(
                domainObject -> domainObject.getString("name", "NA_INVALID_DOMAIN").length()))
        .map(domainObject -> domainObject.getString("id"))
        .orElseThrow(
            () -> new IllegalArgumentException("Invalid domain name: " + dnsRecord.getName()));
  }

  @Override
  public Completable createRecord(Record dnsRecord) {
    dnsRecord = prepareNewRecordForProvider(dnsRecord);

    List<Completable> completables = new ArrayList<>();
    completables.add(operateRecordEntry(dnsRecord, ChangeAction.UPSERT));
    return Completable.mergeDelayError(completables);
  }

  @Override
  public Completable updateRecord(Record dnsRecord, RecordDiff values) {
    dnsRecord.getValues().removeAll(values.getValuesToDelete());
    dnsRecord.getValues().addAll(values.getValuesToAdd());
    // todo  add logic to delete the record when the values are empty and also delete the record
    // from the database
    return operateRecordEntry(dnsRecord, ChangeAction.UPSERT);
  }

  @Override
  public Completable deleteRecord(Record dnsRecord) {
    List<Completable> completables = new ArrayList<>();
    completables.add(operateRecordEntry(dnsRecord, ChangeAction.DELETE));
    return Completable.mergeDelayError(completables);
  }

  private record Route53RecordDetails(Long ttl, Long weight) {}

  public Record prepareNewRecordForProvider(Record record) {

    record.setType(defaultType(record));
    if (!com.dream11.odin.constant.RecordType.WEIGHTED.equals(record.getType())) {
      return record;
    }
    record.setIdentifier(getDefaultIdentifier(record));
    boolean dnsResult = com.dream11.odin.util.DnsUtil.resolveWithCnameCheck(record.getName());
    Route53RecordDetails existingDetails =
        dnsResult ? fetchExistingDetails(record.getName()) : null;
    record.setWeight(setDefaultWeight(record, dnsResult));
    record.setTtlInSeconds(setDefaultTtl(existingDetails));
    return record;
  }

  private RecordType defaultType(Record dto) {
    if (dto.getType() == null || dto.getType().getName().isBlank()) {
      return com.dream11.odin.constant.RecordType.WEIGHTED;
    }
    return dto.getType();
  }

  private String getDefaultIdentifier(Record dto) {
    if (dto.getIdentifier() == null || dto.getIdentifier().isBlank()) {
      return "ods-" + UUID.randomUUID();
    }
    return dto.getIdentifier();
  }

  private Route53RecordDetails fetchExistingDetails(String recordName) {
    try {
      return checkRecordExistsInRoute53(recordName);
    } catch (Exception e) {
      log.warn("Failed to fetch Route53 record details for {}: {}", recordName, e.getMessage());
    }
    return null;
  }

  private long setDefaultWeight(Record dto, boolean dnsResult) {
    if (dto.getWeight() <= 0) {
      return dnsResult ? 0L : 100L;
    }
    return dto.getWeight();
  }

  private long setDefaultTtl(Route53RecordDetails existingDetails) {
    return (existingDetails != null && existingDetails.ttl() != null) ? existingDetails.ttl() : 60L;
  }

  // todo move this to discovery provider interface
  private Route53RecordDetails checkRecordExistsInRoute53(String fqdn) {

    // Determine hosted zone ID based on provider config (same logic as provider)
    io.vertx.core.json.JsonArray domains = getConfig().getJsonArray("domains");
    String hostedZoneId =
        domains.stream()
            .map(io.vertx.core.json.JsonObject.class::cast)
            .filter(obj -> fqdn.endsWith(obj.getString("name", "INVALID")))
            .max(Comparator.comparingInt(obj -> obj.getString("name", "INVALID").length()))
            .map(obj -> obj.getString("id"))
            .orElseThrow(() -> new IllegalArgumentException("Invalid domain for " + fqdn));

    com.amazonaws.services.route53.AmazonRoute53 client = createRoute53Client();

    com.amazonaws.services.route53.model.ListResourceRecordSetsRequest req =
        new com.amazonaws.services.route53.model.ListResourceRecordSetsRequest()
            .withHostedZoneId(hostedZoneId)
            .withStartRecordName(fqdn)
            .withMaxItems("1");

    com.amazonaws.services.route53.model.ListResourceRecordSetsResult result =
        client.listResourceRecordSets(req);

    return result.getResourceRecordSets().stream()
        .filter(rrs -> rrs.getName().equalsIgnoreCase(fqdn + "."))
        .findFirst()
        .map(
            rrs ->
                new Route53RecordDetails(
                    rrs.getTTL() != null ? rrs.getTTL() : null,
                    rrs.getWeight() != null ? rrs.getWeight() : null))
        .orElse(null);
  }
}
