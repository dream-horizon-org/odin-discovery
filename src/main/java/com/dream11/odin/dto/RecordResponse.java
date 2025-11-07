package com.dream11.odin.dto;

import com.dream11.odin.dto.constants.Status;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RecordResponse {

  Status status;
  String message;
  String id;
}
