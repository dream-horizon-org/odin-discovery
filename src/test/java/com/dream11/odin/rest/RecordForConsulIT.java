package com.dream11.odin.rest;

import static com.dream11.odin.oam.MockOAMProviderAccountService.accountNameConsul;
import static com.dream11.odin.util.TestUtil.OBJECT_MAPPER;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.dream11.odin.dto.BatchRequest;
import com.dream11.odin.dto.Record;
import com.dream11.odin.dto.RecordAction;
import com.dream11.odin.dto.constants.Action;
import com.dream11.odin.dto.constants.Status;
import com.dream11.odin.provider.impl.consul.dto.ConsulRequest;
import com.dream11.odin.provider.impl.consul.dto.NodeMeta;
import com.dream11.odin.provider.impl.consul.dto.Service;
import com.dream11.odin.setup.Setup;
import com.dream11.odin.util.TestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.vertx.core.json.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith({Setup.class})
@WireMockTest(httpPort = 8082)
class RecordForConsulIT {

  private static final String CONSUL_API_PATH_REGISTER = "/v1/catalog/register";

  private static final String CONSUL_API_PATH_DEREGISTER = "/v1/catalog/deregister";

  private static final String CONSUL_API_PATH_DESCRIBE = "/v1/catalog/service/";
  private static final String EXTERNAL_NODE = "true";

  private static final String EXTERNAL_PROBE = "true";

  static Connection connection;

  @BeforeAll
  @SneakyThrows
  static void setup() {
    connection = TestUtil.getDatabaseConnection();
    TestUtil.executeSqlFile(connection, "src/test/resources/data", "testdata.sql");
  }

  @Test
  void singleRouteCreated() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "testRecord.example-stag.local";
    List<String> values = List.of("138.23.12.12");

    stubFor(
        put(CONSUL_API_PATH_REGISTER)
            .withRequestBody(
                equalTo(
                    OBJECT_MAPPER.writeValueAsString(
                        buildConsulRequest(recordName, values.get(0)))))
            .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameConsul);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(new Record(recordName, 0, 0, "", values, ""), Action.UPSERT, "1"));
    batchRequest.setRecordActions(recordActions);

    // Act and Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(200);

    TestUtil.assertRecordCreatedInDB(recordName, values);
  }

  @Test
  void multipleRoutesCreated() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName1 = "testRecord1.example-stag.local";
    List<String> values1 = List.of("138.23.12.12", "138.23.12.13");
    String recordName2 = "testRecord2.example-stag.local";
    List<String> values2 = List.of("139.23.12.12", "139.23.12.13");

    stubFor(
        put(CONSUL_API_PATH_REGISTER)
            .withRequestBody(
                equalTo(
                    OBJECT_MAPPER.writeValueAsString(
                        buildConsulRequest(recordName1, values1.get(0)))))
            .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
    stubFor(
        put(CONSUL_API_PATH_REGISTER)
            .withRequestBody(
                equalTo(
                    OBJECT_MAPPER.writeValueAsString(
                        buildConsulRequest(recordName1, values1.get(1)))))
            .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
    stubFor(
        put(CONSUL_API_PATH_REGISTER)
            .withRequestBody(
                equalTo(
                    OBJECT_MAPPER.writeValueAsString(
                        buildConsulRequest(recordName2, values2.get(0)))))
            .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
    stubFor(
        put(CONSUL_API_PATH_REGISTER)
            .withRequestBody(
                equalTo(
                    OBJECT_MAPPER.writeValueAsString(
                        buildConsulRequest(recordName2, values2.get(1)))))
            .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameConsul);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(new Record(recordName1, 0, 0, "", values1, ""), Action.UPSERT, "1"));
    recordActions.add(
        new RecordAction(new Record(recordName2, 0, 0, "", values2, ""), Action.UPSERT, "2"));
    batchRequest.setRecordActions(recordActions);

    // Act and Assert
    given()
        .port(8080)
        .header("orgId", 1L)
        .header("Content-Type", "application/json")
        .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
        .when()
        .put("/v1/record")
        .then()
        .statusCode(200);
    TestUtil.assertRecordCreatedInDB(recordName1, values1);
    TestUtil.assertRecordCreatedInDB(recordName2, values2);
  }

  @Test
  void singleRouteDeleted() throws SQLException, JsonProcessingException {
    // Arrange
    String recordName = "testRecordToDelete.example-stag.local";
    String value = "140.23.12.12";

    stubFor(
        get(CONSUL_API_PATH_DESCRIBE + updateRecordName(recordName))
            .willReturn(
                aResponse().withBody("[{\"Node\":\"nodename\"}]").withStatus(HttpStatus.SC_OK)));
    stubFor(put(CONSUL_API_PATH_DEREGISTER).willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameConsul);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(new Record(recordName, 0, 0, "", List.of(value), ""), Action.DELETE, "1"));
    batchRequest.setRecordActions(recordActions);
    // Act
    String response =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .extract()
            .asString();

    // Assert
    JsonObject responseJson = new JsonObject(response);
    assertEquals(
        Status.SUCCESSFUL.toString(),
        responseJson.getJsonArray("responseList").getJsonObject(0).getString("status"));
    TestUtil.assertRecordDeletedFromDB(recordName);
  }

  @Test
  void deleteNonExistantRecord() throws JsonProcessingException {

    // Arrange
    String recordName = "nonExistantRecord.example-stag.local";
    String value = "140.23.12.12";

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameConsul);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(new Record(recordName, 0, 0, "", List.of(value), ""), Action.DELETE, "1"));
    batchRequest.setRecordActions(recordActions);
    // Act
    String response =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .extract()
            .asString();

    // Assert
    JsonObject responseJson = new JsonObject(response);
    assertEquals(
        Status.SUCCESSFUL.toString(),
        responseJson.getJsonArray("responseList").getJsonObject(0).getString("status"));
  }

  @Test
  void singleRouteUpdated() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "singleRouteUpdate.example-stag.local";
    List<String> values = List.of("138.23.12.13");

    stubFor(
        put(CONSUL_API_PATH_DEREGISTER)
            .withRequestBody(
                equalTo("{\"Node\":\"" + updateRecordName(recordName) + "-node-138.23.12.12\"}"))
            .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    stubFor(
        put(CONSUL_API_PATH_REGISTER)
            .withRequestBody(
                equalTo(
                    OBJECT_MAPPER.writeValueAsString(
                        buildConsulRequest(recordName, values.get(0)))))
            .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameConsul);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(new Record(recordName, 0, 0, "", values, ""), Action.UPSERT, "1"));
    batchRequest.setRecordActions(recordActions);
    // Act
    String response =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .extract()
            .asString();

    // Assert
    JsonObject responseJson = new JsonObject(response);
    assertEquals(
        Status.SUCCESSFUL.toString(),
        responseJson.getJsonArray("responseList").getJsonObject(0).getString("status"));
    TestUtil.assertRecordCreatedInDB(recordName, values);
  }

  @Test
  void singleRouteAppended() throws SQLException, JsonProcessingException {
    // Arrange
    String recordName = "singleRouteAppend.example-stag.local";
    List<String> values = List.of("138.23.12.13", "138.23.12.14");
    for (String val : values) {
      stubFor(
          put(CONSUL_API_PATH_REGISTER)
              .withRequestBody(
                  equalTo(OBJECT_MAPPER.writeValueAsString(buildConsulRequest(recordName, val))))
              .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
    }

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameConsul);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(new Record(recordName, 0, 0, "", values, ""), Action.UPSERT, "1"));
    batchRequest.setRecordActions(recordActions);

    // Act
    String response =
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
            .asString();

    // Assert
    JsonObject responseJson = new JsonObject(response);

    // Check if responseList exists and is not null
    if (responseJson.getJsonArray("responseList") == null) {
      log.error("responseList is null in response: " + response);
      // Check if there's an error field instead
      if (responseJson.containsKey("error")) {
        log.error("ERROR field found: " + responseJson.getString("error"));
      }
      if (responseJson.containsKey("message")) {
        log.error("MESSAGE field found: " + responseJson.getString("message"));
      }
      fail("responseList should not be null. Full response: " + response);
    }

    assertNotNull(responseJson.getJsonArray("responseList"), "responseList should not be null");
    assertTrue(
        responseJson.getJsonArray("responseList").size() > 0, "responseList should not be empty");
    assertEquals(
        Status.SUCCESSFUL.toString(),
        responseJson.getJsonArray("responseList").getJsonObject(0).getString("status"));
    TestUtil.assertRecordCreatedInDB(recordName, values);
  }

  @Test
  void singleRouteValueRemoved() throws SQLException, JsonProcessingException {

    // Arrange
    String recordName = "singleRouteValueRemoved.example-stag.local";
    List<String> values = List.of("138.23.12.14");

    stubFor(
        put(CONSUL_API_PATH_DEREGISTER)
            .withRequestBody(
                equalTo("{\"Node\":\"" + updateRecordName(recordName) + "-node-138.23.12.13\"}"))
            .willReturn(aResponse().withStatus(HttpStatus.SC_OK)));

    BatchRequest batchRequest = new BatchRequest();
    batchRequest.setAccountName(accountNameConsul);
    List<RecordAction> recordActions = new ArrayList<>();
    recordActions.add(
        new RecordAction(new Record(recordName, 0, 0, "", values, ""), Action.UPSERT, "1"));
    batchRequest.setRecordActions(recordActions);
    // Act
    String response =
        given()
            .port(8080)
            .header("orgId", 1L)
            .header("Content-Type", "application/json")
            .body(OBJECT_MAPPER.writeValueAsString(batchRequest))
            .when()
            .put("/v1/record")
            .then()
            .extract()
            .asString();

    // Assert
    JsonObject responseJson = new JsonObject(response);
    assertEquals(
        Status.SUCCESSFUL.toString(),
        responseJson.getJsonArray("responseList").getJsonObject(0).getString("status"));
    TestUtil.assertRecordCreatedInDB(recordName, values);
  }

  private ConsulRequest buildConsulRequest(String recordName, String value) {

    return ConsulRequest.builder()
        .node(buildNodeId(recordName, value))
        .address(value)
        .service(
            Service.builder()
                .tags(List.of("odin_discovery"))
                .discoveryService(updateRecordName(recordName))
                .build())
        .nodeMeta(
            NodeMeta.builder().externalNode(EXTERNAL_NODE).externalProbe(EXTERNAL_PROBE).build())
        .build();
  }

  private String buildNodeId(String recordName, String value) {
    return updateRecordName(recordName) + "-node-" + value;
  }

  private String updateRecordName(String recordName) {
    return recordName.split("\\.")[0];
  }
}
