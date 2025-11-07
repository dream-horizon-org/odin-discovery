package com.dream11.odin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeartbeatRequest {

  String accountName;
  List<Record> records;
  Record dnsRecord;
}
