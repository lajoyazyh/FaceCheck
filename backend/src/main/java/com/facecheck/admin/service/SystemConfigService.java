package com.facecheck.admin.service;

import com.facecheck.admin.api.dto.SystemConfigItemResponse;
import com.facecheck.admin.api.dto.UpdateSystemConfigRequest;
import com.facecheck.admin.model.SystemConfig;
import com.facecheck.admin.model.SystemConfigValueType;
import com.facecheck.admin.repo.SystemConfigRepository;
import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private static final Map<String, Definition> WHITELIST = definitions();

    private final SystemConfigRepository systemConfigRepository;
    private final CurrentUserAccess currentUserAccess;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public SystemConfigService(
            SystemConfigRepository systemConfigRepository,
            CurrentUserAccess currentUserAccess,
            AuditLogService auditLogService,
            ObjectMapper objectMapper
    ) {
        this.systemConfigRepository = systemConfigRepository;
        this.currentUserAccess = currentUserAccess;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<SystemConfigItemResponse> listConfigs() {
        ensureDefaults(actorUserIdOrSystem());
        return systemConfigRepository.findAllByOrderByConfigKeyAsc().stream()
                .filter(config -> WHITELIST.containsKey(config.getConfigKey()))
                .map(SystemConfigItemResponse::from)
                .toList();
    }

    @Transactional
    public SystemConfigItemResponse updateConfig(String configKey, UpdateSystemConfigRequest request) {
        Definition definition = WHITELIST.get(configKey);
        if (definition == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "System config item does not exist");
        }

        validateValue(definition.valueType(), request.configValue());
        ensureDefaults(actorUserIdOrSystem());

        SystemConfig config = systemConfigRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "System config item does not exist"));
        config.setConfigValue(request.configValue().trim());
        config.setUpdatedBy(requireActorUserId());
        SystemConfig saved = systemConfigRepository.save(config);

        auditLogService.recordCurrentActor(
                "SYSTEM_CONFIG_UPDATE",
                "SYSTEM_CONFIG",
                saved.getId(),
                "Administrator updated a system configuration item.",
                Map.of("configKey", saved.getConfigKey(), "valueType", saved.getValueType().name())
        );
        return SystemConfigItemResponse.from(saved);
    }

    private void ensureDefaults(UUID actorUserId) {
        Set<String> existingKeys = systemConfigRepository.findAll().stream()
                .map(SystemConfig::getConfigKey)
                .collect(java.util.stream.Collectors.toSet());

        List<SystemConfig> missingConfigs = WHITELIST.entrySet().stream()
                .filter(entry -> !existingKeys.contains(entry.getKey()))
                .map(entry -> definitionToConfig(entry.getKey(), entry.getValue(), actorUserId))
                .toList();

        if (!missingConfigs.isEmpty()) {
            systemConfigRepository.saveAll(missingConfigs);
        }
    }

    private SystemConfig definitionToConfig(String configKey, Definition definition, UUID actorUserId) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(configKey);
        config.setConfigValue(definition.defaultValue());
        config.setValueType(definition.valueType());
        config.setDescription(definition.description());
        config.setUpdatedBy(actorUserId);
        return config;
    }

    private void validateValue(SystemConfigValueType valueType, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Config value must not be blank.");
        }
        try {
            switch (valueType) {
                case STRING -> {
                }
                case NUMBER -> Double.parseDouble(value);
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new BusinessException(ErrorCode.BAD_REQUEST, "Boolean config values must be true or false.");
                    }
                }
                case JSON -> objectMapper.readTree(value);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Config value does not match the expected type.");
        }
    }

    private UUID requireActorUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return currentUserAccess.requireUserId(authentication);
    }

    private UUID actorUserIdOrSystem() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                return currentUserAccess.requireUserId(authentication);
            } catch (BusinessException ignored) {
            }
        }
        return SYSTEM_USER_ID;
    }

    private static Map<String, Definition> definitions() {
        Map<String, Definition> definitions = new LinkedHashMap<>();
        definitions.put("frs.face-set-name", new Definition("facecheck-default", SystemConfigValueType.STRING,
                "Configured Huawei face set name"));
        definitions.put("frs.similarity-threshold", new Definition("85", SystemConfigValueType.NUMBER,
                "Minimum compare threshold for a successful face match"));
        definitions.put("frs.liveness-enabled", new Definition("false", SystemConfigValueType.BOOLEAN,
                "Reserved liveness switch for later cloud integration"));
        definitions.put("checkin.idempotency-ttl-hours", new Definition("24", SystemConfigValueType.NUMBER,
                "Redis idempotency cache TTL in hours"));
        definitions.put("checkin.rate-limit-window-seconds", new Definition("60", SystemConfigValueType.NUMBER,
                "Anonymous check-in rate-limit time window"));
        definitions.put("checkin.rate-limit-max-requests", new Definition("5", SystemConfigValueType.NUMBER,
                "Maximum anonymous check-in requests per rate-limit window"));
        definitions.put("image.max-file-size-bytes", new Definition("10485760", SystemConfigValueType.NUMBER,
                "Maximum accepted face/check-in image file size in bytes"));
        return definitions;
    }

    private record Definition(String defaultValue, SystemConfigValueType valueType, String description) {
    }
}
