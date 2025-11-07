package com.dream11.odin.dao.entity;

import lombok.Builder;

@Builder
public record RecordDestinationEntity(long id, long recordId, String destination) {}
