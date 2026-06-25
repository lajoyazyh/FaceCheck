package com.facecheck.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.facecheck.common.error.BusinessException;
import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.DeleteObjectResult;
import com.obs.services.model.ObjectMetadata;
import com.obs.services.model.ObsObject;
import com.obs.services.model.PutObjectResult;
import com.obs.services.model.TemporarySignatureRequest;
import com.obs.services.model.TemporarySignatureResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HuaweiObsStorageServiceTest {

    @Test
    void shouldUploadDeleteAndMintPreviewUrl() {
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setObsBucket("facecheck-bucket");
        properties.setObsRegion("cn-test-1");
        properties.setObsEndpoint("https://obs.example.com");
        ObjectKeyStrategy objectKeyStrategy = new ObjectKeyStrategy();
        HuaweiObsStorageService service = new InMemoryHuaweiObsStorageService(properties, objectKeyStrategy);

        String objectKey = objectKeyStrategy.facePhotoKey(UUID.randomUUID(), UUID.randomUUID());
        HuaweiObsStorageService.StoredObject storedObject =
                service.upload(objectKey, "face-bytes".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        assertThat(storedObject.bucket()).isEqualTo("facecheck-bucket");
        assertThat(storedObject.region()).isEqualTo("cn-test-1");
        assertThat(storedObject.objectKey()).isEqualTo(objectKey);
        assertThat(storedObject.storageProvider()).isEqualTo("MOCK_OBS");

        URI previewUrl = service.generatePreviewUrl(objectKey);
        assertThat(previewUrl.toString()).contains("https://obs.example.com/facecheck-bucket/");
        assertThat(previewUrl.toString()).contains(objectKey);

        service.delete(objectKey);

        assertThatThrownBy(() -> service.generatePreviewUrl(objectKey))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldUseHuaweiObsClientForRealStorageOperations() {
        HuaweiCloudProperties properties = new HuaweiCloudProperties();
        properties.setAk("test-ak");
        properties.setSk("test-sk");
        properties.setObsBucket("facecheck-real-bucket");
        properties.setObsRegion("cn-test-1");
        properties.setObsEndpoint("https://obs.cn-test-1.example.com");
        ObjectKeyStrategy objectKeyStrategy = new ObjectKeyStrategy();
        FakeObsClient obsClient = new FakeObsClient("stored-face".getBytes(StandardCharsets.UTF_8));
        HuaweiObsStorageService service =
                new HuaweiObsStorageServiceImpl(properties, objectKeyStrategy, obsClient);

        String objectKey = objectKeyStrategy.checkinAttemptKey(UUID.randomUUID(), UUID.randomUUID());
        HuaweiObsStorageService.StoredObject storedObject =
                service.upload(objectKey, "upload-face".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        assertThat(obsClient.uploadedBucket).isEqualTo("facecheck-real-bucket");
        assertThat(obsClient.uploadedObjectKey).isEqualTo(objectKey);
        assertThat(obsClient.uploadedContentType).isEqualTo("image/jpeg");
        assertThat(storedObject.storageProvider()).isEqualTo("HUAWEI_OBS");
        assertThat(service.read(objectKey)).isEqualTo("stored-face".getBytes(StandardCharsets.UTF_8));

        URI previewUrl = service.generatePreviewUrl(objectKey);
        assertThat(previewUrl.toString()).isEqualTo("https://signed.example.com/" + objectKey);

        service.delete(objectKey);
        assertThat(obsClient.deletedObjectKey).isEqualTo(objectKey);
    }

    private static final class FakeObsClient extends ObsClient {

        private final byte[] readableContent;
        private String uploadedBucket;
        private String uploadedObjectKey;
        private String uploadedContentType;
        private String deletedObjectKey;

        private FakeObsClient(byte[] readableContent) {
            super("ak", "sk", "https://obs.example.com");
            this.readableContent = readableContent;
        }

        @Override
        public PutObjectResult putObject(
                String bucketName,
                String objectKey,
                InputStream input,
                ObjectMetadata metadata
        ) throws ObsException {
            this.uploadedBucket = bucketName;
            this.uploadedObjectKey = objectKey;
            this.uploadedContentType = metadata.getContentType();
            try {
                input.readAllBytes();
            } catch (IOException exception) {
                throw new AssertionError(exception);
            }
            return null;
        }

        @Override
        public ObsObject getObject(String bucketName, String objectKey) throws ObsException {
            ObsObject object = new ObsObject();
            object.setBucketName(bucketName);
            object.setObjectKey(objectKey);
            object.setObjectContent(new ByteArrayInputStream(readableContent));
            return object;
        }

        @Override
        public DeleteObjectResult deleteObject(String bucketName, String objectKey) throws ObsException {
            this.deletedObjectKey = objectKey;
            return null;
        }

        @Override
        public TemporarySignatureResponse createTemporarySignature(TemporarySignatureRequest request) {
            return new TemporarySignatureResponse("https://signed.example.com/" + request.getObjectKey());
        }
    }
}
