package com.dream11.odin.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class IpUtilTest {
  @Test
  void testIsIpAddress_ValidIpAddresses() {
    List<String> validIps = Arrays.asList("192.168.1.1", "10.0.0.1", "172.16.0.1");
    assertTrue(IPUtil.isIpAddress(validIps));
  }

  @Test
  void testIsIpAddress_InvalidIpAddresses() {
    List<String> invalidIps =
        Arrays.asList("999.999.999.999", "abc.def.ghi.jkl", "256.256.256.256");
    assertFalse(IPUtil.isIpAddress(invalidIps));
  }

  @Test
  void testIsIpAddress_MixedIpAddresses() {
    List<String> mixedIps = Arrays.asList("192.168.1.1", "invalid.ip.address", "10.0.0.1");
    assertFalse(IPUtil.isIpAddress(mixedIps));
  }
}
