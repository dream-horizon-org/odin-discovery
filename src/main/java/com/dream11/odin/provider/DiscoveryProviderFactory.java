package com.dream11.odin.provider;

import static com.dream11.odin.exception.Error.INVALID_DISCOVERY_PROVIDER;
import static com.dream11.odin.provider.impl.consul.ConsulDiscoveryProvider.CONFIG_HOST;
import static com.dream11.odin.provider.impl.consul.ConsulDiscoveryProvider.CONFIG_PORT;
import static com.dream11.odin.provider.impl.consul.ConsulDiscoveryProvider.TIMEOUT_SECS;
import static com.dream11.odin.util.JsonUtil.sortJsonObject;

import com.dream11.odin.config.ApplicationConfig;
import com.dream11.odin.dto.constants.DiscoveryProviderType;
import com.dream11.odin.provider.impl.consul.ConsulDiscoveryProvider;
import com.dream11.odin.provider.impl.route53.Route53DiscoveryProvider;
import com.dream11.odin.util.ExceptionUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.tuple.Pair;

@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class DiscoveryProviderFactory {

  final WebClient webClient;

  final ApplicationConfig applicationConfig;

  final ObjectMapper objectMapper;
  private static final long CACHE_DURATION_MINUTES = 59;

  // Update the providerCache declaration
  private final Map<String, Pair<DiscoveryProvider, Instant>> providerCache = new HashMap<>();

  public static String generateKey(DiscoveryProviderType discoveryProviderType, JsonObject config) {
    return discoveryProviderType.name() + DigestUtils.sha256Hex(sortJsonObject(config).toString());
  }

  public DiscoveryProvider createDiscoveryProvider(
      DiscoveryProviderType discoveryProviderType, JsonObject config) {
    String cacheKey = generateKey(discoveryProviderType, config);
    Pair<DiscoveryProvider, Instant> cachedProvider = providerCache.get(cacheKey);
    if (cachedProvider != null) {
      if (discoveryProviderType == DiscoveryProviderType.AWS_ROUTE53) {
        if (Duration.between(cachedProvider.getRight(), Instant.now()).toMinutes()
            < CACHE_DURATION_MINUTES) {
          return cachedProvider.getLeft();
        }
      } else {
        return cachedProvider.getLeft();
      }
    }
    DiscoveryProvider provider =
        switch (discoveryProviderType) {
          case CONSUL -> new ConsulDiscoveryProvider(
              webClient,
              config.getString(CONFIG_HOST),
              Integer.parseInt(config.getString(CONFIG_PORT)),
              config.getInteger(TIMEOUT_SECS),
              objectMapper,
              mergeTags(config),
              config);
          case AWS_ROUTE53 -> new Route53DiscoveryProvider(
              webClient, objectMapper, config, applicationConfig);
          default -> throw ExceptionUtil.getException(INVALID_DISCOVERY_PROVIDER);
        };
    providerCache.put(cacheKey, Pair.of(provider, Instant.now()));
    return provider;
  }

  private List<String> mergeTags(JsonObject config) {
    List<String> tags = new ArrayList<>(applicationConfig.getTags());
    config.getJsonArray("tags", new JsonArray()).forEach(value -> tags.add(value.toString()));
    return tags;
  }
}
