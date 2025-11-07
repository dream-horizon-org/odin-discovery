package com.dream11.odin.dao.entity;

import com.dream11.odin.constant.ClientType;
import lombok.Builder;

@Builder
public record RecordEntity(
    Long id,
    String name,
    long providerId,
    long ttlInSeconds,
    long weight,
    String identifier,
    ClientType clientType) {}
