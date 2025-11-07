package com.dream11.odin.dto;

import com.dream11.odin.dto.constants.Action;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecordAction {
  @JsonProperty("record")
  Record dnsRecord;

  Action action;
  String id;
}
