package com.dream11.odin.constant;

import static com.dream11.odin.exception.Error.RECORD_TYPE_VALIDATION;

import java.util.stream.Stream;

public enum RecordType {
  SIMPLE("simple"),
  WEIGHTED("weighted");

  private final String name;

  RecordType(String type) {
    this.name = type;
  }

  public String getName() {
    return name;
  }

  public static RecordType getValueDefaultWeighted(String recordType) {
    if (recordType == null || recordType.isBlank()) {
      return RecordType.WEIGHTED;
    }
    return Stream.of(RecordType.values())
        .filter(type -> type.getName().equals(recordType))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException(RECORD_TYPE_VALIDATION.getErrorMessage()));
  }
}
