package com.dream11.odin.dto.constants;

import static com.dream11.odin.exception.Error.INVALID_DISCOVERY_PROVIDER;

import com.dream11.odin.util.ExceptionUtil;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum DiscoveryProviderType {
  AWS_ROUTE53("R53"),
  CONSUL("Consul");

  private final String name;

  public static List<String> getDiscoveryProviderTypes() {
    return Arrays.stream(DiscoveryProviderType.values())
        .map(DiscoveryProviderType::getName)
        .collect(Collectors.toList());
  }

  public static DiscoveryProviderType customValueOf(String name) {
    return Arrays.stream(values())
        .filter(value -> value.getName().equalsIgnoreCase(name))
        .findFirst()
        .orElseThrow(() -> ExceptionUtil.getException(INVALID_DISCOVERY_PROVIDER, name));
  }
}
