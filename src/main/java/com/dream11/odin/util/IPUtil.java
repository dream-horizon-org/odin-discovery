package com.dream11.odin.util;

import java.util.List;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class IPUtil {
  public boolean isIpAddress(List<String> values) {
    String ipv4Regex = "^((\\d{1,3})\\.){3}(\\d{1,3})$";
    return values.stream().allMatch(value -> value.matches(ipv4Regex));
  }
}
