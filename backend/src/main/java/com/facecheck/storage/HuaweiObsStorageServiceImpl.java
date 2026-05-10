package com.facecheck.storage;

import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HuaweiObsStorageServiceImpl implements HuaweiObsStorageService {

    private final HuaweiCloudProperties properties;
    private final ObjectKeyStrategy objectKeyStrategy;
    private final Map<String, byte[]> objectStore = new ConcurrentHashMap<>();

    public HuaweiObsStorageServiceImpl(HuaweiCloudProperties properties, ObjectKeyStrategy objectKeyStrategy) {
        this.properties = properties;
        this.objectKeyStrategy = objectKeyStrategy;
    }

    @Override
    public StoredObject upload(String objectKey, byte[] content, String contentType) {
        objectStore.put(objectKey, content.clone());
        return new StoredObject(
                bucket(),
                region(),
                objectKey,
                contentType,
                content.length,
                "HUAWEI_OBS"
        );
    }

    @Override
    public byte[] read(String objectKey) {
        byte[] content = objectStore.get(objectKey);
        if (content == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Object does not exist");
        }
        return content.clone();
    }

    @Override
    public void delete(String objectKey) {
        objectStore.remove(objectKey);
    }

    @Override
    public URI generatePreviewUrl(String objectKey) {
        if (!objectStore.containsKey(objectKey)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Object does not exist");
        }
        String endpoint = properties.getObsEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://mock-obs.local";
        }
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        return URI.create(base + "/" + bucket() + "/" + objectKey + "?expiresAt=" + Instant.now().plusSeconds(900));
    }

    public ObjectKeyStrategy objectKeyStrategy() {
        return objectKeyStrategy;
    }

    private String bucket() {
        return properties.getObsBucket() == null || properties.getObsBucket().isBlank()
                ? "facecheck-test"
                : properties.getObsBucket();
    }

    private String region() {
        return properties.getObsRegion() == null || properties.getObsRegion().isBlank()
                ? "test-region"
                : properties.getObsRegion();
    }
}
