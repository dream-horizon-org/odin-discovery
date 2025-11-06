package com.dream11.odin.util;

import com.google.protobuf.Struct;
import com.google.protobuf.util.JsonFormat;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonUtil {
  public JsonObject getJsonObjectFromNestedJson(JsonObject json, String flattenedKey) {
    JsonObject cur = json;
    String[] keys = flattenedKey.split("\\.");
    for (String key : keys) {
      if (!cur.containsKey(key)) {
        return new JsonObject();
      }
      cur = cur.getJsonObject(key);
    }
    return cur;
  }

  @SneakyThrows
  public String getJson(Struct struct) {
    return JsonFormat.printer().omittingInsignificantWhitespace().print(struct);
  }

  public static JsonObject jsonMerge(Object[] objs) {
    return Arrays.stream(objs)
        .map(JsonObject::mapFrom)
        .reduce(new JsonObject(), JsonObject::mergeIn);
  }

  public static JsonObject sortJsonObject(JsonObject jsonObject) {
    Map<String, Object> sortedMap = new TreeMap<>();
    for (String key : jsonObject.fieldNames()) {
      Object value = jsonObject.getValue(key);
      if (value instanceof JsonObject jsonObjectToSort) {
        sortedMap.put(key, sortJsonObject(jsonObjectToSort));
      } else if (value instanceof JsonArray jsonArrayToSort) {
        sortedMap.put(key, sortJsonArray(jsonArrayToSort));
      } else {
        sortedMap.put(key, value);
      }
    }
    return new JsonObject(sortedMap);
  }

  public static JsonArray sortJsonArray(JsonArray jsonArray) {
    JsonArray sortedArray = new JsonArray();
    for (Object value : jsonArray) {
      if (value instanceof JsonObject jsonObject) {
        sortedArray.add(sortJsonObject(jsonObject));
      } else if (value instanceof JsonArray jsonArrayValue) {
        sortedArray.add(sortJsonArray(jsonArrayValue));
      } else {
        sortedArray.add(value);
      }
    }

    // Sort the array if it contains JsonObjects or JsonArrays
    if (!jsonArray.isEmpty()
        && (jsonArray.getValue(0) instanceof JsonObject
            || jsonArray.getValue(0) instanceof JsonArray)) {
      sortedArray.getList().sort((o1, o2) -> o1.toString().compareTo(o2.toString()));
    }

    return sortedArray;
  }
}
