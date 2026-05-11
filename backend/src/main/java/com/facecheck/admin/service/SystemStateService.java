package com.facecheck.admin.service;

import com.facecheck.admin.api.dto.SystemStateResponse;
import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.facecheck.infrastructure.health.DependencyHealthService;
import com.facecheck.storage.HuaweiObsStorageService;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class SystemStateService {

    private final DependencyHealthService dependencyHealthService;
    private final HuaweiCloudProperties huaweiCloudProperties;
    private final ObjectProvider<FaceRecognitionProvider> faceRecognitionProvider;
    private final ObjectProvider<HuaweiObsStorageService> huaweiObsStorageService;

    public SystemStateService(
            DependencyHealthService dependencyHealthService,
            HuaweiCloudProperties huaweiCloudProperties,
            ObjectProvider<FaceRecognitionProvider> faceRecognitionProvider,
            ObjectProvider<HuaweiObsStorageService> huaweiObsStorageService
    ) {
        this.dependencyHealthService = dependencyHealthService;
        this.huaweiCloudProperties = huaweiCloudProperties;
        this.faceRecognitionProvider = faceRecognitionProvider;
        this.huaweiObsStorageService = huaweiObsStorageService;
    }

    public SystemStateResponse currentState() {
        DependencyHealthService.HealthSnapshot healthSnapshot = dependencyHealthService.currentStatus();
        Map<String, String> statuses = healthSnapshot.dependencies().stream()
                .collect(Collectors.toMap(
                        DependencyHealthService.DependencyStatus::name,
                        dependency -> dependency.available() ? "UP" : "DOWN"
                ));

        return new SystemStateResponse(
                statuses.getOrDefault("postgres", "DOWN"),
                statuses.getOrDefault("redis", "DOWN"),
                statuses.getOrDefault("rabbitmq", "DOWN"),
                cloudCapabilityStatus(faceRecognitionProvider.getIfAvailable() != null),
                cloudCapabilityStatus(huaweiObsStorageService.getIfAvailable() != null),
                Instant.now()
        );
    }

    private String cloudCapabilityStatus(boolean beanPresent) {
        if (!beanPresent) {
            return "DOWN";
        }
        return huaweiCloudProperties.isEnabled() ? "UP" : "DEGRADED";
    }
}
