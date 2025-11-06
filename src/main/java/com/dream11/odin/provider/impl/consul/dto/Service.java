package com.dream11.odin.provider.impl.consul.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class Service {
  @JsonProperty("Service")
  String discoveryService;

  List<String> tags;
}
