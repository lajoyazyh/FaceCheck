package com.facecheck.storage;

import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.HttpMethodEnum;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class HuaweiObsStorageServiceImpl implements HuaweiObsStorageService {

    private static final long PREVIEW_URL_EXPIRES_SECONDS = 900L;

    private final HuaweiCloudProperties properties;
    private final ObjectKeyStrategy objectKeyStrategy;
    private final ObsClient obsClient;

    public HuaweiObsStorageServiceImpl(HuaweiCloudProperties properties, ObjectKeyStrategy objectKeyStrategy) {
        this(properties, objectKeyStrategy, createClient(properties));
    }

    HuaweiObsStorageServiceImpl(
            HuaweiCloudProperties properties,
            ObjectKeyStrategy objectKeyStrategy,
            ObsClient obsClient
    ) {
        this.properties = properties;
        this.objectKeyStrategy = objectKeyStrategy;
        this.obsClient = obsClient;
    }

    @Override
    public StoredObject upload(String objectKey, byte[] content, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            metadata.setContentLength((long) content.length);
            obsClient.putObject(bucket(), objectKey, new ByteArrayInputStream(content), metadata);
            return new StoredObject(
                    bucket(),
                    region(),
                    objectKey,
                    contentType,
                    content.length,
                    "HUAWEI_OBS"
            );
        } catch (ObsException exception) {
            throw storageException("Failed to upload object to Huawei OBS", exception);
        }
    }

    @Override
    public byte[] read(String objectKey) {
        try {
            ObsObject object = obsClient.getObject(bucket(), objectKey);
            try (InputStream input = object.getObjectContent()) {
                return input.readAllBytes();
            }
        } catch (ObsException exception) {
            throw storageException("Failed to read object from Huawei OBS", exception);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Failed to read object stream from Huawei OBS");
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            obsClient.deleteObject(bucket(), objectKey);
        } catch (ObsException exception) {
            throw storageException("Failed to delete object from Huawei OBS", exception);
        }
    }

    @Override
    public URI generatePreviewUrl(String objectKey) {
        try {
            TemporarySignatureRequest request = new TemporarySignatureRequest(
                    HttpMethodEnum.GET,
                    bucket(),
                    objectKey,
                    null,
                    PREVIEW_URL_EXPIRES_SECONDS
            );
            TemporarySignatureResponse response = obsClient.createTemporarySignature(request);
            return URI.create(response.getSignedUrl());
        } catch (ObsException exception) {
            throw storageException("Failed to generate Huawei OBS preview URL", exception);
        }
    }

    public ObjectKeyStrategy objectKeyStrategy() {
        return objectKeyStrategy;
    }

    private static ObsClient createClient(HuaweiCloudProperties properties) {
        requireConfigured(properties.getAk(), "FRS_AK");
        requireConfigured(properties.getSk(), "FRS_SK");
        requireConfigured(properties.getObsEndpoint(), "OBS_ENDPOINT");
        return new ObsClient(properties.getAk(), properties.getSk(), properties.getObsEndpoint());
    }

    private static void requireConfigured(String value, String variableName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, variableName + " is required for Huawei OBS");
        }
    }

    private String bucket() {
        if (properties.getObsBucket() == null || properties.getObsBucket().isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "OBS_BUCKET is required for Huawei OBS");
        }
        return properties.getObsBucket();
    }

    private String region() {
        if (properties.getObsRegion() == null || properties.getObsRegion().isBlank()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, "OBS_REGION is required for Huawei OBS");
        }
        return properties.getObsRegion();
    }

    private BusinessException storageException(String message, ObsException exception) {
        if (exception.getResponseCode() == 404) {
            return new BusinessException(ErrorCode.NOT_FOUND, "Object does not exist");
        }
        return new BusinessException(ErrorCode.INTERNAL_ERROR, message);
    }
}
