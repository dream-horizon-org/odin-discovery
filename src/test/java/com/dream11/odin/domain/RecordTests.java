package com.dream11.odin.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dream11.odin.constant.ClientType;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordTests {

  @Test
  void testCheckIdentityIsEqual_BothIdentifiersNull() {
    com.dream11.odin.dto.Record record1 = new com.dream11.odin.dto.Record();
    record1.setName("test");
    record1.setValues(List.of("value1"));
    record1.setIdentifier(null);
    com.dream11.odin.domain.Record record2 =
        com.dream11.odin.domain.Record.create(record1, ClientType.CONTROLLER);
    record2.setIdentifier(null);
    assertTrue(record2.checkIdentityIsEqual(record1));
  }

  @Test
  void testCheckIdentityIsEqual_OneIdentifierNull() {
    com.dream11.odin.dto.Record record1 = new com.dream11.odin.dto.Record();
    record1.setName("test");
    record1.setValues(List.of("value1"));
    record1.setIdentifier(null);
    com.dream11.odin.domain.Record record2 =
        com.dream11.odin.domain.Record.create(record1, ClientType.CONTROLLER);
    record2.setIdentifier("id1");
    assertFalse(record2.checkIdentityIsEqual(record1));
  }

  @Test
  void testCheckIdentityIsEqual_BothIdentifiersEqual() {
    com.dream11.odin.dto.Record record1 = new com.dream11.odin.dto.Record();
    record1.setName("test");
    record1.setValues(List.of("value1"));
    record1.setIdentifier("id1");
    com.dream11.odin.domain.Record record2 =
        com.dream11.odin.domain.Record.create(record1, ClientType.CONTROLLER);
    record2.setIdentifier("id1");
    assertTrue(record2.checkIdentityIsEqual(record1));
  }

  @Test
  void testCheckIdentityIsEqual_BothIdentifiersNotEqual() {
    com.dream11.odin.dto.Record record1 = new com.dream11.odin.dto.Record();
    record1.setName("test");
    record1.setValues(List.of("value1"));
    record1.setIdentifier("id1");
    com.dream11.odin.domain.Record record2 =
        com.dream11.odin.domain.Record.create(record1, ClientType.CONTROLLER);
    record2.setIdentifier("id2");
    assertFalse(record2.checkIdentityIsEqual(record1));
  }
}
