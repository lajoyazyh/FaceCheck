package com.facecheck.admin.api.dto;

import java.time.Instant;

public record SystemStateResponse(
        String database,
        String redis,
        String rabbitmq,
        String frs,
        String obs,
        Instant checkedAt
) {
}
