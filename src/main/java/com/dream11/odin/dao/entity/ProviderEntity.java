package com.dream11.odin.dao.entity;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProviderEntity {

  long id;
  String name;
  String account;
  long orgId;
  String config;
  String configHash;
}
