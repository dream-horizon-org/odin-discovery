package com.dream11.odin.domain;

import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
public class RecordDiff {

  final List<String> valuesToAdd;
  final List<String> valuesToDelete;
}
