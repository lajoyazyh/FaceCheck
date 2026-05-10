package com.facecheck.storage;

import java.net.URI;

public interface HuaweiObsStorageService {

    StoredObject upload(String objectKey, byte[] content, String contentType);

    byte[] read(String objectKey);

    void delete(String objectKey);

    URI generatePreviewUrl(String objectKey);

    record StoredObject(
            String bucket,
            String region,
            String objectKey,
            String contentType,
            long sizeBytes,
            String storageProvider
    ) {
    }
}
