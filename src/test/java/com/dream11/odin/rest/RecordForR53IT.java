package com.dream11.odin.rest;

import static com.dream11.odin.constant.Constants.DEFAULT_TTL_IN_SECONDS;
import static com.dream11.odin.exception.Error.ACCOUNT_NAME_CANNOT_BE_EMPTY;
import static com.dream11.odin.exception.Error.ACTION_REQUIRED_IN_RECORD_ACTION;
import static com.dream11.odin.exception.Error.DISCOVERY_SERVICE_NOT_FOUND;
import static com.dream11.odin.exception.Error.DUPLICATE_ID_FOUND_IN_RECORD_ACTIONS;
import static com.dream11.odin.exception.Error.MULTIPLE_DISCOVERY_PROVIDER;
import static com.dream11.odin.exception.Error.RECORD_ACTIONS_CANNOT_BE_EMPTY;
import static com.dream11.odin.exception.Error.RECORD_NAME_CANNOT_BE_EMPTY;
import static com.dream11.odin.exception.Error.RECORD_TYPE_CANNOT_CHANGE_ONCE_CREATED;
import static com.dream11.odin.exception.Error.RECORD_TYPE_VALIDATION;
import static com.dream11.odin.exception.Error.RECORD_VALUE_CANNOT_BE_EMPTY;
import static com.dream11.odin.exception.Error.TTL_INVALID;
import static com.dream11.odin.oam.MockOAMProviderAccountService.accountNameAWS;
import static com.dream11.odin.oam.MockOAMProviderAccountService.accountNameAWSMultipleActive;
import static com.dream11.odin.oam.MockOAMProviderAccountService.accountNameAWSNoActive;
import static com.dream11.odin.setup.Setup.region;
import static com.dream11.odin.util.TestUtil.OBJECT_MAPPER;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.route53.AmazonRoute53;
import com.amazonaws.services.route53.AmazonRoute53ClientBuilder;
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ListResourceRecordSetsResult;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;
import com.dream11.odin.constant.RecordType;
import com.dream11.odin.dto.BatchRequest;
import com.dream11.odin.dto.RecordAction;
import com.dream11.odin.dto.constants.Action;
import com.dream11.odin.dto.constants.Status;
import com.dream11.odin.setup.Setup;
import com.dream11.odin.util.IPUtil;
import com.dream11.odin.util.TestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith({Setup.class})
@WireMockTest(httpPort = 8082)
@lombok.extern.slf4j.Slf4j
class RecordForR53IT {
  static Connection connection;

  @BeforeAll
  @SneakyThrows
  static void setUp() {
    connection = TestUtil.getDatabaseConnection();
  }

  private void assertRecordOnRoute53(
      String recordName, List<String> values, long expectedTTL, Long expectedWeight) {
    // Step 1: Instantiate the AmazonRoute53 client configured for LocalStack
    AmazonRoute53 route53Client =
        AmazonRoute53ClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    "http://localhost:" + System.getProperty("awsEndpointPort", "4566"), region))
            .build();

    // Assuming hostedZoneId is available or retrieved earlier
    String hostedZoneId = System.getProperty("PRIVATE_HOSTED_ZONE"); // For example.local
    if (recordName.endsWith("example.com")) {
      hostedZoneId = System.getProperty("PUBLIC_HOSTED_ZONE"); // For example.com
    }

    // Step 2: List the resource record sets for the hosted zone
    ListResourceRecordSetsRequest request =
        new ListResourceRecordSetsRequest().withHostedZoneId(hostedZoneId);
    ListResourceRecordSetsResult result = route53Client.listResourceRecordSets(request);

    boolean isIP = IPUtil.isIpAddress(values);
    // Step 3: Find the record set that matches the provided record name
    boolean recordFound = false;
    for (ResourceRecordSet recordSet : result.getResourceRecordSets()) {
      // check if the values are not ip and the record set is of not type cname then dont check
      if (!isIP && !recordSet.getType().equalsIgnoreCase("CNAME")) {
        continue;
      }
      if (recordSet.getName().equals(recordName + ".")) { // Route53 record names end with a dot
        List<String> recordSetValues =
            recordSet.getResourceRecords().stream().map(ResourceRecord::getValue).toList();
        // Step 4: Assert that the record set exists and its values match the provided values
        assertEquals(
            new HashSet<>(values), new HashSet<>(recordSetValues), "Record values do not match");
        assertEquals(expectedTTL, recordSet.getTTL(), "TTL does not match");

        // Step 5: If the record type is weighted, assert that the weight matches
        if (expectedWeight != null && recordSet.getWeight() != null) {
          assertEquals(expectedWeight, recordSet.getWeight(), "Weight does not match");
        }
        recordFound = true;
        break;
      }
    }
    assertTrue(recordFound, "Record not found in Route53");
  }

  private void assertRecordDeleted(
      String recordName, List<String> values, long expectedTTL, Long expectedWeight) {
    // Step 1: Instantiate the AmazonRoute53 client configured for LocalStack
    AmazonRoute53 route53Client =
        AmazonRoute53ClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(
                    "http://localhost:" + System.getProperty("awsEndpointPort", "4566"), region))
            .build();

    // Assuming hostedZoneId is available or retrieved earlier
    String hostedZoneId = System.getProperty("PRIVATE_HOSTED_ZONE"); // For example.local
    if (recordName.endsWith("example.com")) {
      hostedZoneId = System.getProperty("PUBLIC_HOSTED_ZONE"); // For example.com
    }

    // Step 2: List the resource record sets for the hosted zone
    ListResourceRecordSetsRequest request =
        new ListResourceRecordSetsRequest().withHostedZoneId(hostedZoneId);
    ListResourceRecordSetsResult result = route53Client.listResourceRecordSets(request);

    boolean isIP = IPUtil.isIpAddress(values);
    // Step 3: Find the record set that matches the provided record name
    boolean recordFound = false;
    for (ResourceRecordSet recordSet : result.getResourceRecordSets()) {
      // check if the values are not ip and the record set is of not type cname then dont check
      if (!isIP && !recordSet.getType().equalsIgnoreCase("CNAME")) {
        continue;
      }
      if (recordSet.getName().equals(recordName + ".")) { // Route53 record names end with a dot
        List<String> recordSetValues =
            recordSet.getResourceRecords().stream().map(ResourceRecord::getValue).toList();
        // Step 4: Assert that the record set exists and its values match the provided values
        assertEquals(
            new HashSet<>(values), new HashSet<>(recordSetValues), "Record values do not match");
        assertEquals(expectedTTL, recordSet.getTTL(), "TTL does not match");

        // Step 5: If the record type is weighted, assert that the weight matches
        if (expectedWeight != null && recordSet.getWeight() != null) {
          assertEquals(expectedWeight, recordSet.getWeight(), "Weight does not match");
        }
        recordFound = true;
        break;
      }
    }
    assertFalse(recordFound, "Record found in Route53");
  }

  @Test
  void singleRouteCreatedIP() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "test.example.local";
    List<String> values = List.of("8.8.8.8", "8.8.1.1");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
    log.info(responseBody);

    TestUtil.assertRecordCreatedInDB(recordName, values);
    assertRecordOnRoute53(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
  }

  @Test
  void singleRouteCreatedIPNoActive() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "test.example.local";
    List<String> values = List.of("8.8.8.8", "8.8.1.1");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWSNoActive);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            //            .statusCode(200)
            //            .body("responseList[0].message",
            // equalTo(NO_DISCOVERY_PROVIDER_FOUND.getErrorMessage()))
            .extract()
            .body()
            .asString();
    log.info(responseBody);
  }

  @Test
  void singleRouteCreatedIPMultipleProvider() throws SQLException, JsonProcessingException {
    // Arrange
    String recordName = "test.example.local";
    List<String> values = List.of("8.8.8.8", "8.8.1.1");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWSMultipleActive);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 2L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(400)
            .body(
                "error.message",
                equalTo(MULTIPLE_DISCOVERY_PROVIDER.getErrorMessage().replace("%s", "R53")))
            .extract()
            .body()
            .asString();
    log.info(responseBody);
  }

  @Test
  void singleRouteCreatedCNAME() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "test.example.local";
    List<String> values = List.of("alias.example.com", "alias2.example.com");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info("singleRouteCreatedCNAME response :" + responseBody);

    TestUtil.assertRecordCreatedInDB(recordName, values);
    assertRecordOnRoute53(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
  }

  @Test
  void deleteRecordCNAME() throws SQLException, JsonProcessingException {
    String recordName = "test.example.local";
    List<String> values = List.of("alias.example.com", "alias2.example.com");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info("deleteRecordCNAME :" + responseBody);
    // Arrange
    recordName = "test.example.local";
    values = List.of("alias.example.com", "alias2.example.com");

    batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.DELETE,
            "1"));
    batchRequest.setRecordActions(recordActions);
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info("deleteRecordCNAME2" + responseBody);

    TestUtil.assertRecordDeletedFromDB(recordName);
    assertRecordDeleted(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
  }

  @Test
  void deleteRecordIP() throws SQLException, JsonProcessingException {
    String recordName = "test.example.local";
    List<String> values = List.of("8.8.8.8", "8.8.1.1");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
    log.info(responseBody);
    // Arrange
    recordName = "test.example.local";
    values = List.of("8.8.8.8", "8.8.1.1");

    batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.DELETE,
            "1"));
    batchRequest.setRecordActions(recordActions);
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info(responseBody);

    TestUtil.assertRecordDeletedFromDB(recordName);
    assertRecordDeleted(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
  }

  @Test
  void upsertRecordIP() throws SQLException, JsonProcessingException {
    // Arrange
    String recordName = "upsert.test.example.local";
    List<String> values =
        List.of("10.10.10.10", "10.10.10.11"); // Example IP addresses for an A record
    long ttlInSeconds = 300; // Example TTL

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, ttlInSeconds, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);

    // Act
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
    log.info("upsertRecordIP: " + responseBody);
    // Assert
    TestUtil.assertRecordCreatedInDB(recordName, values); // Check record creation in DB
    assertRecordOnRoute53(
        recordName, values, ttlInSeconds, null); // Check record upsertion in Route53

    values = List.of("8.8.8.8", "8.8.1.1"); // Example IP addresses for an A record

    recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, ttlInSeconds, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    // Act
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
    log.info("upsertRecordIP 2: " + responseBody);

    // Assert
    TestUtil.assertRecordCreatedInDB(recordName, values); // Check record creation in DB
    assertRecordOnRoute53(
        recordName, values, ttlInSeconds, null); // Check record upsertion in Route53
  }

  @Test
  void upsertRecordCNAME() throws SQLException, JsonProcessingException {
    // Arrange
    String recordName = "upsert.test.example.local";
    List<String> values = List.of("test.com", "test1.com"); // Example IP addresses for an A record
    long ttlInSeconds = 300; // Example TTL

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, ttlInSeconds, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);

    // Act
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
    log.info("upsertRecordCNAME " + responseBody);
    // Assert
    TestUtil.assertRecordCreatedInDB(recordName, values); // Check record creation in DB
    assertRecordOnRoute53(
        recordName, values, ttlInSeconds, null); // Check record upsertion in Route53

    values = List.of("tes.dom", "tes1.dom"); // Example IP addresses for an A record

    recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, ttlInSeconds, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    // Act
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
    log.info("upsertRecordCNAME 2 " + responseBody);

    // Assert
    TestUtil.assertRecordCreatedInDB(recordName, values); // Check record creation in DB
    assertRecordOnRoute53(
        recordName, values, ttlInSeconds, null); // Check record upsertion in Route53
  }

  @Test
  void upsertRequestWithEmptyRecordActions() throws JsonProcessingException {
    // Arrange
    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    batchRequest.setRecordActions(new ArrayList<>()); // Empty recordActions list

    // Expected response
    String expectedResponse = "{\"responseList\":[]}";

    // Act
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200) // Expecting HTTP 200 status code
            .extract()
            .body()
            .asString();

    // Assert
    assertEquals(expectedResponse, responseBody, "Expected empty responseList in the response");
  }

  @Test
  void validUpsertRequestWithTypeWeighted() throws JsonProcessingException, SQLException {
    // Arrange
    String recordName = "tests49.example.local";
    List<String> values = List.of("8.8.8.8");
    long weight = 100;
    String identifier = "tests49";
    long ttlInSeconds = 60;
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName,
                ttlInSeconds,
                weight,
                identifier,
                values,
                RecordType.WEIGHTED.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200) // Expecting HTTP 200 status code
            .extract()
            .body()
            .asString();
    log.info("validUpsertRequestWithTypeWeighted " + responseBody);
    // Assert
    TestUtil.assertRecordCreatedInDB(recordName, values); // Check record creation in DB
    assertRecordOnRoute53(
        recordName, values, ttlInSeconds, weight); // Check record upsertion in Route53
  }

  @Test
  void validDeleteRequestWithTypeWeighted() throws JsonProcessingException, SQLException {
    // Arrange
    String recordName = "tests49.example.local";
    List<String> values = List.of("8.8.8.8");
    long weight = 80;
    String identifier = "tests49";
    long ttlInSeconds = 60;
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName,
                ttlInSeconds,
                weight,
                identifier,
                values,
                RecordType.WEIGHTED.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200) // Expecting HTTP 200 status code
            .extract()
            .body()
            .asString();
    log.info("validDeleteRequestWithTypeWeighted1" + responseBody);

    recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName,
                ttlInSeconds,
                weight,
                identifier,
                values,
                RecordType.WEIGHTED.getName()),
            Action.DELETE,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200) // Expecting HTTP 200 status code
            .extract()
            .body()
            .asString();
    log.info("validDeleteRequestWithTypeWeighted2" + responseBody);

    TestUtil.assertRecordDeletedFromDB(recordName);
    assertRecordDeleted(recordName, values, ttlInSeconds, weight);
  }

  @Test
  void upsertRequestWithAccountNameMissing() throws JsonProcessingException {
    // Arrange
    String recordName = "tests54.example.local";
    List<String> values = List.of("8.8.8.8");
    String actionId = "4";
    BatchRequest batchRequest = new BatchRequest();
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, DEFAULT_TTL_IN_SECONDS, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(400)
            .body("error.message", equalTo(ACCOUNT_NAME_CANNOT_BE_EMPTY.getErrorMessage()))
            .body("error.cause", equalTo(ACCOUNT_NAME_CANNOT_BE_EMPTY.getErrorMessage()))
            .body("error.code", equalTo(ACCOUNT_NAME_CANNOT_BE_EMPTY.getErrorCode()))
            .extract()
            .asString();
    log.info("upsertRequestWithAccountNameMissing" + responseBody);
  }

  @Test
  void upsertRequestWithRecordNameMissing() throws JsonProcessingException {
    // Arrange
    String invalidAccountName = accountNameAWS;
    List<String> values = List.of("8.8.8.8");
    String actionId = "4";
    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(invalidAccountName);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                null, DEFAULT_TTL_IN_SECONDS, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(400)
            .body("error.message", equalTo(RECORD_NAME_CANNOT_BE_EMPTY.getErrorMessage()))
            .body("error.cause", equalTo(RECORD_NAME_CANNOT_BE_EMPTY.getErrorMessage()))
            .body("error.code", equalTo(RECORD_NAME_CANNOT_BE_EMPTY.getErrorCode()))
            .extract()
            .asString();
    log.info("upsertRequestWithRecordNameMissing" + responseBody);
  }

  @Test
  void upsertRequestWithTypeWeightedMissingIdentifier() throws JsonProcessingException {
    // Arrange
    String recordName = "tests49.example.local";
    List<String> values = List.of("8.8.8.8");
    long ttlInSeconds = 60;
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, ttlInSeconds, 100, null, values, RecordType.WEIGHTED.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);
    String responseBodytest =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .body("responseList[0].status", equalTo(Status.SUCCESSFUL.toString()))
            .body("responseList[0].id", equalTo(actionId))
            .extract()
            .asString();
    log.info("upsertRequestWithTypeWeightedMissingIdentifier" + responseBodytest);
  }

  @Test
  void deleteRequestWithMissingRecordDetails() throws JsonProcessingException {
    // Arrange
    long ttlInSeconds = 60;
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                null, ttlInSeconds, 100, null, null, RecordType.WEIGHTED.getName()),
            Action.DELETE,
            actionId));
    batchRequest.setRecordActions(recordActions);
    String responseBodytest =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(400)
            .body("error.message", equalTo(RECORD_NAME_CANNOT_BE_EMPTY.getErrorMessage()))
            .body("error.cause", equalTo(RECORD_NAME_CANNOT_BE_EMPTY.getErrorMessage()))
            .body("error.code", equalTo(RECORD_NAME_CANNOT_BE_EMPTY.getErrorCode()))
            .extract()
            .body()
            .asString();
    log.info("deleteRequestWithMissingRecordDetails" + responseBodytest);
  }

  @Test
  void deleteNonExistentRecord() throws JsonProcessingException {
    // Arrange
    String nonExistentRecordName = "nonexistent.example.local";
    List<String> values = List.of("8.8.8.8");
    long weight = 80;
    String identifier = "nonexistent";
    long ttlInSeconds = 60;
    String actionId = "999";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                nonExistentRecordName,
                ttlInSeconds,
                weight,
                identifier,
                values,
                RecordType.WEIGHTED.getName()),
            Action.DELETE,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record") // Assuming the endpoint for deletion is the same as for
        // creation/upsertion
        .then()
        .statusCode(200) // Expecting HTTP 404 status code for not found
        .body("responseList[0].status", equalTo(Status.SUCCESSFUL.toString()))
        .body("responseList[0].id", equalTo(actionId));
  }

  @Test
  void requestWithoutRecordActions() throws JsonProcessingException {
    // Arrange
    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);

    // Act & Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(400) // Expecting HTTP 400 Bad Request
        .body("error.message", equalTo(RECORD_ACTIONS_CANNOT_BE_EMPTY.getErrorMessage()))
        .body("error.cause", equalTo(RECORD_ACTIONS_CANNOT_BE_EMPTY.getErrorMessage()))
        .body("error.code", equalTo(RECORD_ACTIONS_CANNOT_BE_EMPTY.getErrorCode()));
  }

  @Test
  void requestWithActionAbsent() throws JsonProcessingException {
    // Arrange
    String recordName = "tests66.example.local";
    List<String> values = List.of("8.8.8.8");
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 0, 0, "", values, RecordType.SIMPLE.getName()),
            null, // Action is intentionally left null to simulate the missing action scenario
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(200) // Expecting HTTP 400 Bad Request status code
        .body("responseList[0].status", equalTo(Status.FAILED.toString()))
        .body(
            "responseList[0].message", equalTo(ACTION_REQUIRED_IN_RECORD_ACTION.getErrorMessage()))
        .body("responseList[0].id", equalTo(actionId));
  }

  @Test
  void upsertRequestWithInvalidRecordType() throws JsonProcessingException {
    // Arrange
    String recordName = "tests74.example.local";
    List<String> values = List.of("8.8.8.8");
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, DEFAULT_TTL_IN_SECONDS, 0, "", values, "Invalid"),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(
            200) // The expected status code might be a typo, usually, it should be 400 for Bad
        // Request.
        .body("responseList[0].status", equalTo(Status.FAILED.toString()))
        .body("responseList[0].message", equalTo(RECORD_TYPE_VALIDATION.getErrorMessage()))
        .body("responseList[0].id", equalTo(actionId));
  }

  @Test
  void upsertRequestWithInvalidRecordValues() throws JsonProcessingException {
    // Arrange
    String recordName = "tests77.example.local";
    List<String> values = new ArrayList<>(); // Empty values list to simulate the invalid scenario
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, DEFAULT_TTL_IN_SECONDS, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(
            200) // The expected status code might be a typo, usually, it should be 400 for Bad
        // Request.
        .body("responseList[0].status", equalTo(Status.FAILED.toString()))
        .body("responseList[0].message", equalTo(RECORD_VALUE_CANNOT_BE_EMPTY.getErrorMessage()))
        .body("responseList[0].id", equalTo(actionId));
  }

  @Test
  void upsertRequestWithInvalidTTL() throws JsonProcessingException {
    // Arrange
    String recordName = "tests80.example.local";
    List<String> values = List.of("8.8.8.8");
    long invalidTTL = -1; // Invalid TTL value
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, invalidTTL, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(200) // The expected status code might be a typo, usually, it should be
        .body("responseList[0].status", equalTo(Status.FAILED.toString()))
        .body("responseList[0].id", equalTo(actionId))
        .body("responseList[0].message", equalTo(TTL_INVALID.getErrorMessage()));
  }

  @Test
  void upsertRequestWithMissingIdentifierForWeightedType() throws JsonProcessingException {
    // Arrange
    String recordName = "tests85.example.local";
    List<String> values = List.of("8.8.8.8");
    long weight = 80; // Weight provided but identifier is missing
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName,
                DEFAULT_TTL_IN_SECONDS,
                weight,
                "",
                values,
                RecordType.WEIGHTED.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(200) // Expecting HTTP 400 Bad Request
        .body("responseList[0].status", equalTo(Status.SUCCESSFUL.toString()))
        .body("responseList[0].id", equalTo(actionId));
  }

  @Test
  void idempotencyUpsertCheck() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "test.example.local";
    List<String> values = List.of("8.8.8.8", "8.8.1.1");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info("idempotencyUpsertCheck " + responseBody);

    TestUtil.assertRecordCreatedInDB(recordName, values);
    assertRecordOnRoute53(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
    // rerun the same request
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info("idempotencyUpsertCheck 2 " + responseBody);

    TestUtil.assertRecordCreatedInDB(recordName, values);
    assertRecordOnRoute53(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
  }

  @Test
  void idempotencyDeleteRecordCNAME() throws SQLException, JsonProcessingException {
    String recordName = "test.example.local";
    List<String> values = List.of("alias.example.com", "alias2.example.com");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info("response :", responseBody);
    // Arrange
    recordName = "test.example.local";
    values = List.of("alias.example.com", "alias2.example.com");
    String actionId = "1";

    batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.DELETE,
            actionId));
    batchRequest.setRecordActions(recordActions);
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info(responseBody);

    TestUtil.assertRecordDeletedFromDB(recordName);
    assertRecordDeleted(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .body("responseList[0].status", equalTo(Status.SUCCESSFUL.toString()))
            .body("responseList[0].id", equalTo(actionId))
            .extract()
            .body()
            .asString();

    log.info(responseBody);

    TestUtil.assertRecordDeletedFromDB(recordName);
    assertRecordDeleted(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
  }

  @Test
  void upsertRequestWithDuplicateId() throws JsonProcessingException {
    // Arrange
    String accountName = accountNameAWS;
    String recordName = "tests55.example.local";
    List<String> values = List.of("8.8.8.8");
    String actionId = "1"; // Duplicate ID

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountName);
    List<RecordAction> recordActions = new ArrayList<>();
    // Adding the same action twice to simulate duplicate ID scenario
    RecordAction recordAction =
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, DEFAULT_TTL_IN_SECONDS, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            actionId);
    recordActions.add(recordAction);
    recordActions.add(recordAction); // Duplicate action
    batchRequest.setRecordActions(recordActions);

    // Act & Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(400) // Expecting HTTP 400 Bad Request due to duplicate ID
        .body("error.message", equalTo(DUPLICATE_ID_FOUND_IN_RECORD_ACTIONS.getErrorMessage()))
        .body("error.cause", equalTo(DUPLICATE_ID_FOUND_IN_RECORD_ACTIONS.getErrorMessage()))
        .body("error.code", equalTo(DUPLICATE_ID_FOUND_IN_RECORD_ACTIONS.getErrorCode()));
  }

  @Test
  void singleRouteCreatedIPPublic() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "test.example.com";
    List<String> values = List.of("8.8.8.8", "8.8.1.1");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

    log.info("singleRouteCreatedIPPublic " + responseBody);

    TestUtil.assertRecordCreatedInDB(recordName, values);
    assertRecordOnRoute53(recordName, values, DEFAULT_TTL_IN_SECONDS, null);
  }

  @AfterAll
  static void deleteAllRecords() throws SQLException {
    String query = "DELETE FROM provider;DELETE  from record_destination;DELETE from record;";
    Statement statement = connection.createStatement();
    statement.executeUpdate(query);
  }

  @Test
  void upsertRequestWithTypeChange() throws JsonProcessingException, SQLException {
    // Arrange
    String recordName = "tests49.example.local";
    List<String> values = List.of("8.8.8.8");
    long weight = 100;
    String identifier = "tests49";
    long ttlInSeconds = 60;
    String actionId = "4";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName,
                ttlInSeconds,
                weight,
                identifier,
                values,
                RecordType.WEIGHTED.getName()),
            Action.UPSERT,
            actionId));
    batchRequest.setRecordActions(recordActions);

    // Act
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200) // Expecting HTTP 200 status code
            .extract()
            .body()
            .asString();
    log.info("validUpsertRequestWithTypeWeighted " + responseBody);
    // Assert
    TestUtil.assertRecordCreatedInDB(recordName, values); // Check record creation in DB
    assertRecordOnRoute53(
        recordName, values, ttlInSeconds, weight); // Check record upsertion in Route53

    // Arrange

    batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, identifier, values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    responseBody =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(200)
            .body(
                "responseList[0].message",
                equalTo(RECORD_TYPE_CANNOT_CHANGE_ONCE_CREATED.getErrorMessage()))
            .extract()
            .body()
            .asString();
    log.info(responseBody);
  }

  @Test
  void noDiscoveryServiceFound() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "test.example.local";
    List<String> values = List.of("8.8.8.8", "8.8.1.1");

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameAWS);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(
            new com.dream11.odin.dto.Record(
                recordName, 60, 0, "", values, RecordType.SIMPLE.getName()),
            Action.UPSERT,
            "1"));
    batchRequest.setRecordActions(recordActions);
    String responseBody =
        given()
            .port(8080)
            .header("orgId", 3L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .statusCode(400)
            .body("error.message", equalTo(DISCOVERY_SERVICE_NOT_FOUND.getErrorMessage()))
            .extract()
            .body()
            .asString();
    log.info(responseBody);
  }
}
