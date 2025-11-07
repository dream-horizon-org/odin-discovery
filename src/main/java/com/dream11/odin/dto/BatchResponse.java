package com.dream11.odin.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchResponse {

  List<RecordResponse> responseList;
}
