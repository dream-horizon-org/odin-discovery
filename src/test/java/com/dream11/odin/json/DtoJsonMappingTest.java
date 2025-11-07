package com.dream11.odin.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dream11.odin.dto.RecordAction;
import com.dream11.odin.provider.impl.consul.dto.ConsulRequest;
import com.dream11.odin.provider.impl.consul.dto.NodeMeta;
import com.dream11.odin.provider.impl.consul.dto.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class DtoJsonMappingTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testJsonToRecordActionMapping() throws Exception {
    String json =
        "{\n"
            + "    \"action\": \"UPSERT\",\n"
            + "    \"id\": \"1\",\n"
            + "    \"record\": {\n"
            + "        \"name\": \"tests4887.example-stag.local\",\n"
            + "        \"values\": [\"8.8.8.8\"]\n"
            + "    }\n"
            + "}";

    RecordAction recordAction = objectMapper.readValue(json, RecordAction.class);

    assertNotNull(recordAction);
    assertEquals("UPSERT", recordAction.getAction().toString());
    assertEquals("1", recordAction.getId());
    assertNotNull(recordAction.getDnsRecord());
    assertEquals("tests4887.example-stag.local", recordAction.getDnsRecord().getName());
    assertArrayEquals(new String[] {"8.8.8.8"}, recordAction.getDnsRecord().getValues().toArray());
  }

  @Test
  void testConsulRequestJsonStructure() throws Exception {
    // Create a ConsulRequest object
    ConsulRequest consulRequest =
        ConsulRequest.builder()
            .node("test-node")
            .address("127.0.0.1")
            .service(
                Service.builder()
                    .tags(List.of("tag1", "tag2"))
                    .discoveryService("test-service")
                    .build())
            .nodeMeta(NodeMeta.builder().externalNode("true").externalProbe("true").build())
            .build();

    String json = objectMapper.writeValueAsString(consulRequest);

    // Expected JSON string
    String expectedJson =
        """
                {
                  "node": "test-node",
                  "address": "127.0.0.1",
                  "service": {
                    "Service": "test-service",
                    "tags": ["tag1", "tag2"]
                  },
                  "nodeMeta": {
                    "external-node": "true",
                    "external-probe": "true"
                  }
                }
                """;

    // Compare the generated JSON string with the expected JSON string
    assertEquals(objectMapper.readTree(expectedJson), objectMapper.readTree(json));
  }
}
