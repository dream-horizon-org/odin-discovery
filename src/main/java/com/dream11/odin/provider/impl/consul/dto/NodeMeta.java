package com.dream11.odin.provider.impl.consul.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class NodeMeta {

  @JsonProperty("external-node")
  String externalNode;

  @JsonProperty("external-probe")
  String externalProbe;
}
