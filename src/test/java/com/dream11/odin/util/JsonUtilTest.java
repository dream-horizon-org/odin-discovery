package com.dream11.odin.util;

import static com.dream11.odin.util.JsonUtil.sortJsonArray;
import static com.dream11.odin.util.JsonUtil.sortJsonObject;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;

class JsonUtilTest {
  @Test
  void testSort() {
    JsonObject input = new JsonObject().put("b", 2).put("a", 1).put("c", true).put("d", "string");

    JsonObject expected =
        new JsonObject().put("a", 1).put("b", 2).put("c", true).put("d", "string");

    JsonObject result = sortJsonObject(input);
    assertEquals(expected, result);
  }

  @Test
  void testSortJsonObject_Nested() {
    JsonObject input =
        new JsonObject().put("b", new JsonObject().put("d", 4).put("c", 3)).put("a", 1);

    JsonObject expected =
        new JsonObject().put("a", 1).put("b", new JsonObject().put("c", 3).put("d", 4));

    JsonObject result = sortJsonObject(input);
    assertEquals(expected, result);
  }

  @Test
  void testSortJsonObject_WithArray() {
    JsonObject input = new JsonObject().put("b", new JsonArray().add(3).add(2).add(1)).put("a", 1);

    JsonObject expected =
        new JsonObject().put("a", 1).put("b", new JsonArray().add(3).add(2).add(1));

    JsonObject result = sortJsonObject(input);
    assertEquals(expected, result);
  }

  @Test
  void testSortJsonObject_Empty() {
    JsonObject input = new JsonObject();
    JsonObject expected = new JsonObject();

    JsonObject result = sortJsonObject(input);
    assertEquals(expected, result);
  }

  @Test
  void testSortJsonArray_Simple() {
    JsonArray input = new JsonArray().add(3).add(1).add(2);
    JsonArray expected = new JsonArray().add(3).add(1).add(2);

    JsonArray result = sortJsonArray(input);
    assertEquals(expected, result);
  }

  @Test
  void testSortJsonArray_Nested() {
    JsonArray input =
        new JsonArray()
            .add(new JsonArray().add(3).add(1).add(2))
            .add(new JsonObject().put("b", 2).put("a", 1));

    JsonArray expected =
        new JsonArray()
            .add(new JsonArray().add(3).add(1).add(2))
            .add(new JsonObject().put("a", 1).put("b", 2));

    JsonArray result = sortJsonArray(input);
    assertEquals(expected, result);
  }

  @Test
  void testSortJsonArray_WithObject() {
    JsonArray input = new JsonArray().add(new JsonObject().put("b", 2).put("a", 1)).add(3);

    JsonArray expected = new JsonArray().add(3).add(new JsonObject().put("a", 1).put("b", 2));

    JsonArray result = sortJsonArray(input);
    assertEquals(expected, result);
  }

  @Test
  void testSortJsonArray_Empty() {
    JsonArray input = new JsonArray();
    JsonArray expected = new JsonArray();

    JsonArray result = sortJsonArray(input);
    assertEquals(expected, result);
  }

  @Test
  void testHashJsonArray() {
    String jsonString =
        """
                {"test":"test1",
                "test2":1,
                "test3":[0,1,2,3],
                "test4":{"test5":"test6"},
                "test7":[{"test8":"test9"},{"test10":"test11"}],
                "test12":[[1,2,3],[4,5,6],[7,8,9]],
                "test15":true
                }
                """;
    String jsonString1 =
        """
                {
                "test7":[{"test10":"test11"},{"test8":"test9"}],
                "test12":[[4,5,6],[1,2,3],[7,8,9]],
                "test15":true,
                "test":"test1",
                "test2":1,
                "test3":[0,1,2,3],
                "test4":{"test5":"test6"}
                }
                """;
    String expected = sortJsonObject(new JsonObject(jsonString)).toString();

    String actual = sortJsonObject(new JsonObject(jsonString1)).toString();

    assertEquals(expected, actual);
    assertEquals(DigestUtils.sha256Hex(expected), DigestUtils.sha256Hex(actual));
  }
}
