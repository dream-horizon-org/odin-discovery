package com.dream11.odin.domain;

import com.dream11.odin.constant.ClientType;
import com.dream11.odin.constant.RecordType;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Data
@Builder
public class Record {

  String name;
  @NonNull List<String> values;
  long ttlInSeconds;
  RecordType type;
  long weight;
  String identifier;
  ClientType clientType;

  /*
    Meant to check if all the destinations of both the records are the same.
    TTL and weight are not considered as they are properties which can be updated independently.
  */
  public boolean equalTo(com.dream11.odin.dto.Record recordDto) {
    if (recordDto == null) {
      return false;
    }
    if (!this.name.equals(recordDto.getName())) return false;
    if (values.size() != recordDto.getValues().size()) return false;
    if (this.type != null && !this.type.getName().equals(recordDto.getType())) return false;
    if (this.identifier != null && !this.identifier.equals(recordDto.getIdentifier()))
      return false; // if null it is assumed to be simple
    if (this.ttlInSeconds != recordDto.getTtlInSeconds()) return false;
    // Weight and TTL should also match for weighted records; for SIMPLE we ignore weight.
    if (this.type == RecordType.WEIGHTED && this.weight != recordDto.getWeight()) return false;

    return values.containsAll(recordDto.getValues());
  }

  public boolean checkIdentityIsEqual(com.dream11.odin.dto.Record recordDto) {
    if (recordDto == null) {
      return false;
    }
    if (this.getType() != null
        && recordDto.getType() != null
        && !recordDto.getType().isEmpty()
        && !this.getType().getName().equals(recordDto.getType())) {
      return false;
    }
    if (!this.name.equals(recordDto.getName())) return false;
    if (this.identifier == null && recordDto.getIdentifier() == null) {
      return true;
    }
    if (this.identifier == null || recordDto.getIdentifier() == null) {
      return false;
    }
    return this.identifier.equals(recordDto.getIdentifier());
  }

  public boolean checkIdentityIsEqualWithoutIdentifier(com.dream11.odin.dto.Record recordDto) {
    if (recordDto == null) {
      return false;
    }
    if (this.getType() != null
        && recordDto.getType() != null
        && !recordDto.getType().isEmpty()
        && !this.getType().getName().equals(recordDto.getType())) {
      return false;
    }
    if (!this.name.equals(recordDto.getName())) return false;
    return true;
  }

  public RecordDiff getDiff(@NonNull List<String> target) {
    List<String> toAdd = target.stream().filter(value -> !getValues().contains(value)).toList();
    List<String> toDelete = getValues().stream().filter(value -> !target.contains(value)).toList();
    return new RecordDiff(toAdd, toDelete);
  }

  public static Record create(com.dream11.odin.dto.Record recordDto, ClientType clientType) {
    return Record.builder()
        .name(recordDto.getName())
        .ttlInSeconds(recordDto.getTtlInSeconds())
        .weight(recordDto.getWeight())
        .values(recordDto.getValues())
        .type(RecordType.getValueDefaultWeighted(recordDto.getType()))
        .identifier(recordDto.getIdentifier())
        .clientType(clientType)
        .build();
  }
}
