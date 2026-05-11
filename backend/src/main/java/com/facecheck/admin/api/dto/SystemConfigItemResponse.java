package com.facecheck.admin.api.dto;

import com.facecheck.admin.model.SystemConfig;
import com.facecheck.admin.model.SystemConfigValueType;
import java.time.Instant;

public record SystemConfigItemResponse(
        String configKey,
        String configValue,
        SystemConfigValueType valueType,
        String description,
        Instant updatedAt
) {

    public static SystemConfigItemResponse from(SystemConfig config) {
        return new SystemConfigItemResponse(
                config.getConfigKey(),
                config.getConfigValue(),
                config.getValueType(),
                config.getDescription(),
                config.getUpdatedAt()
        );
    }
}
