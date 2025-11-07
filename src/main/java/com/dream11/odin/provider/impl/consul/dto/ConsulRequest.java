package com.dream11.odin.provider.impl.consul.dto;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class ConsulRequest {

  String node;
  String address;
  Service service;
  NodeMeta nodeMeta;
}
