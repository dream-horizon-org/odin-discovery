package com.dream11.odin.util;

import static com.dream11.odin.constant.Constants.DISCOVERY_PROVIDER_SERVICE_CATEGORY;
import static com.dream11.odin.constant.Constants.IS_ACTIVE;
import static com.dream11.odin.exception.Error.DISCOVERY_SERVICE_NOT_FOUND;

import com.dream11.odin.dao.entity.ProviderEntity;
import com.dream11.odin.dto.constants.DiscoveryProviderType;
import com.dream11.odin.dto.v1.ProviderAccount;
import com.dream11.odin.dto.v1.ProviderServiceAccount;
import com.dream11.odin.exception.Error;
import com.dream11.odin.grpc.provideraccount.v1.GetAllProviderAccountsResponse;
import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

@UtilityClass
public class OamUtil {
  final Value FALSE_DEFAULT_VALUE = Value.newBuilder().setBoolValue(false).build();

  public static List<ProviderServiceAccount> consolidateProviderServiceAccounts(
      List<ProviderAccount> linkedAccounts) {
    return linkedAccounts.stream().flatMap(account -> account.getServicesList().stream()).toList();
  }

  public Map<String, ProviderEntity> extractActiveDiscoveryProvider(
      GetAllProviderAccountsResponse providerAccountResponse, long orgId) {
    Map<ProviderServiceAccount, String> servicesWithAccountName =
        providerAccountResponse.getAccountsList().stream()
            .flatMap(
                account ->
                    Stream.concat(
                        account.getAccount().getServicesList().stream()
                            .map(service -> Map.entry(service, account.getAccount().getName())),
                        consolidateProviderServiceAccounts(account.getLinkedAccountsList()).stream()
                            .map(service -> Map.entry(service, account.getAccount().getName()))))
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing));
    List<ProviderServiceAccount> discoveryServices =
        servicesWithAccountName.keySet().stream()
            .filter(
                service ->
                    DISCOVERY_PROVIDER_SERVICE_CATEGORY.equals(service.getCategory())
                        && DiscoveryProviderType.getDiscoveryProviderTypes()
                            .contains(service.getName()))
            .toList();
    if (discoveryServices.isEmpty()) {
      throw ExceptionUtil.getException(DISCOVERY_SERVICE_NOT_FOUND);
    }
    return discoveryServices.stream()
        .map(
            service ->
                getActiveDomainAndProviderMap(service, orgId, servicesWithAccountName.get(service)))
        .flatMap(map -> map.entrySet().stream())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (existing, replacement) -> {
                  throw ExceptionUtil.getException(
                      Error.MULTIPLE_DISCOVERY_PROVIDER, existing.getName());
                }));
  }

  private static Map<String, ProviderEntity> getActiveDomainAndProviderMap(
      ProviderServiceAccount service, long orgId, String accountName) {
    Map<String, ProviderEntity> providerEntityMap = new HashMap<>();
    ProviderEntity providerEntity =
        ProviderEntity.builder()
            .orgId(orgId)
            .account(accountName)
            .name(service.getName())
            .config(JsonUtil.getJson(service.getData()))
            .build();

    service
        .getData()
        .getFieldsMap()
        .getOrDefault(
            "domains", Value.newBuilder().setListValue(ListValue.getDefaultInstance()).build())
        .getListValue()
        .getValuesList()
        .stream()
        .filter(
            value ->
                value
                    .getStructValue()
                    .getFieldsMap()
                    .getOrDefault(IS_ACTIVE, FALSE_DEFAULT_VALUE)
                    .getBoolValue())
        .forEach(
            value -> {
              String providerDomain =
                  value
                      .getStructValue()
                      .getFieldsMap()
                      .getOrDefault(
                          "name", Value.newBuilder().setStringValue("NA_INVALID_DOMAIN").build())
                      .getStringValue();
              if (!providerDomain.isBlank() && providerEntityMap.containsKey(providerDomain)) {
                throw ExceptionUtil.getException(Error.MULTIPLE_DISCOVERY_PROVIDER, providerDomain);
              }
              if (!providerDomain.isBlank()) {
                providerEntityMap.put(providerDomain, providerEntity);
              }
            });

    return providerEntityMap;
  }
}
