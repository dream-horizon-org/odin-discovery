package com.dream11.odin.rest;

import static io.restassured.RestAssured.given;

import com.dream11.odin.setup.Setup;
import io.vertx.junit5.VertxExtension;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@Slf4j
@ExtendWith({VertxExtension.class, Setup.class})
class HealthcheckIT {

  @Test
  void healthcheckTest() {

    given().port(8080).when().get("/healthcheck").then().statusCode(200);
  }
}
