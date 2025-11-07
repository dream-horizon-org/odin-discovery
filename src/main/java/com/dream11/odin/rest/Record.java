package com.dream11.odin.rest;

import com.dream11.odin.dto.BatchRequest;
import com.dream11.odin.dto.BatchResponse;
import com.dream11.odin.service.RecordService;
import com.google.inject.Inject;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("v1/record")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class Record {

  final RecordService recordService;

  @PUT
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @ApiResponse(
      content = @Content(schema = @Schema(implementation = String.class)),
      description = "Record service")
  public CompletionStage<BatchResponse> batchActions(
      @HeaderParam("orgId") Long orgId, BatchRequest batchRequest) {
    log.info("Received BatchRequest: {}", batchRequest);

    return recordService.processBatch(orgId, batchRequest).toCompletionStage();
  }
}
