package com.facecheck.face.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import com.facecheck.auth.service.PasswordService;
import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.face.model.FaceDetectStatus;
import com.facecheck.face.model.FacePhoto;
import com.facecheck.face.model.FaceRegisterStatus;
import com.facecheck.face.model.HuaweiFaceRef;
import com.facecheck.face.model.HuaweiFaceRefStatus;
import com.facecheck.face.repo.FacePhotoRepository;
import com.facecheck.face.repo.HuaweiFaceRefRepository;
import com.facecheck.identity.model.User;
import com.facecheck.identity.model.UserRole;
import com.facecheck.identity.model.UserStatus;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.storage.HuaweiObsStorageService;
import com.facecheck.storage.ObjectKeyStrategy;
import com.facecheck.support.RedisRabbitContainerSupport;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.amqp.core.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FacePhotoRegisterConsumerTest extends RedisRabbitContainerSupport {

    @Autowired
    private FacePhotoRegisterConsumer consumer;

    @Autowired
    private FacePhotoRepository facePhotoRepository;

    @Autowired
    private HuaweiFaceRefRepository huaweiFaceRefRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private HuaweiObsStorageService storageService;

    @Autowired
    private ObjectKeyStrategy objectKeyStrategy;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private StubFaceRecognitionProvider faceRecognitionProvider;

    @Value("${facecheck.messaging.face-photo.register-retry-queue}")
    private String registerRetryQueue;

    @Value("${facecheck.messaging.face-photo.register-dlq-queue}")
    private String registerDlqQueue;

    @BeforeEach
    void setUp() {
        huaweiFaceRefRepository.deleteAll();
        facePhotoRepository.deleteAll();
        userRepository.deleteAll();
        rabbitAdmin.purgeQueue(registerRetryQueue, true);
        rabbitAdmin.purgeQueue(registerDlqQueue, true);
        faceRecognitionProvider.reset();
    }

    @Test
    void shouldPersistActiveReferenceWhenRegistrationSucceeds() throws Exception {
        FacePhoto photo = createPendingPhoto("success-user");
        faceRecognitionProvider.enqueueDetectResult(new FaceRecognitionProvider.DetectFaceResult(1, true, null, "detect-1"));
        faceRecognitionProvider.enqueueEnrollResult(new FaceRecognitionProvider.EnrollFaceResult("frs-face-1", "enroll-1"));

        consumer.consume(new FacePhotoRegisterTask(photo.getId(), photo.getUserId(), 0));

        FacePhoto reloadedPhoto = facePhotoRepository.findById(photo.getId()).orElseThrow();
        assertThat(reloadedPhoto.getDetectStatus()).isEqualTo(FaceDetectStatus.PASSED);
        assertThat(reloadedPhoto.getRegisterStatus()).isEqualTo(FaceRegisterStatus.ACTIVE);

        HuaweiFaceRef faceRef = huaweiFaceRefRepository.findByFacePhotoId(photo.getId()).orElseThrow();
        assertThat(faceRef.getStatus()).isEqualTo(HuaweiFaceRefStatus.ACTIVE);
        assertThat(faceRef.getFrsFaceId()).isEqualTo("frs-face-1");
        assertThat(faceRef.getExternalFields()).containsEntry("userId", photo.getUserId().toString());
    }

    @Test
    void shouldFailWhenNoFaceIsDetected() throws Exception {
        FacePhoto photo = createPendingPhoto("no-face-user");
        faceRecognitionProvider.enqueueDetectResult(new FaceRecognitionProvider.DetectFaceResult(0, false, "NO_FACE", "detect-2"));

        consumer.consume(new FacePhotoRegisterTask(photo.getId(), photo.getUserId(), 0));

        FacePhoto reloadedPhoto = facePhotoRepository.findById(photo.getId()).orElseThrow();
        assertThat(reloadedPhoto.getDetectStatus()).isEqualTo(FaceDetectStatus.FAILED);
        assertThat(reloadedPhoto.getRegisterStatus()).isEqualTo(FaceRegisterStatus.FAILED);
        assertThat(reloadedPhoto.getFailureCode()).isEqualTo("NO_FACE");
        assertThat(huaweiFaceRefRepository.findByFacePhotoId(photo.getId())).isEmpty();
    }

    @Test
    void shouldFailWhenMultipleFacesAreDetected() throws Exception {
        FacePhoto photo = createPendingPhoto("multiple-face-user");
        faceRecognitionProvider.enqueueDetectResult(new FaceRecognitionProvider.DetectFaceResult(2, false, "MULTIPLE_FACES", "detect-3"));

        consumer.consume(new FacePhotoRegisterTask(photo.getId(), photo.getUserId(), 0));

        FacePhoto reloadedPhoto = facePhotoRepository.findById(photo.getId()).orElseThrow();
        assertThat(reloadedPhoto.getFailureCode()).isEqualTo("MULTIPLE_FACES");
        assertThat(reloadedPhoto.getRegisterStatus()).isEqualTo(FaceRegisterStatus.FAILED);
    }

    @Test
    void shouldFailWhenProviderFlagsLowQuality() throws Exception {
        FacePhoto photo = createPendingPhoto("low-quality-user");
        faceRecognitionProvider.enqueueDetectResult(
                new FaceRecognitionProvider.DetectFaceResult(1, false, "LOW_QUALITY_IMAGE", "detect-4"));

        consumer.consume(new FacePhotoRegisterTask(photo.getId(), photo.getUserId(), 0));

        FacePhoto reloadedPhoto = facePhotoRepository.findById(photo.getId()).orElseThrow();
        assertThat(reloadedPhoto.getFailureCode()).isEqualTo("LOW_QUALITY_IMAGE");
        assertThat(reloadedPhoto.getRegisterStatus()).isEqualTo(FaceRegisterStatus.FAILED);
    }

    @Test
    void shouldRepublishRetryableFailuresToRetryQueue() throws Exception {
        FacePhoto photo = createPendingPhoto("timeout-user");
        faceRecognitionProvider.enqueueDetectException(new RetryableFacePhotoException("FRS_TIMEOUT", "Temporary timeout"));

        consumer.consume(new FacePhotoRegisterTask(photo.getId(), photo.getUserId(), 0));

        FacePhoto reloadedPhoto = facePhotoRepository.findById(photo.getId()).orElseThrow();
        assertThat(reloadedPhoto.getRegisterStatus()).isEqualTo(FaceRegisterStatus.PENDING);
        assertThat(reloadedPhoto.getFailureCode()).isEqualTo("FRS_TIMEOUT");

        FacePhotoRegisterTask message = readTask(registerRetryQueue);
        assertThat(message.retryCount()).isEqualTo(1);
    }

    @Test
    void shouldRouteExhaustedRetryableFailuresToDlq() throws Exception {
        FacePhoto photo = createPendingPhoto("dlq-user");
        faceRecognitionProvider.enqueueDetectException(new RetryableFacePhotoException("FRS_TIMEOUT", "Temporary timeout"));

        consumer.consume(new FacePhotoRegisterTask(photo.getId(), photo.getUserId(), FacePhotoRegisterConsumer.MAX_RETRIES));

        FacePhoto reloadedPhoto = facePhotoRepository.findById(photo.getId()).orElseThrow();
        assertThat(reloadedPhoto.getRegisterStatus()).isEqualTo(FaceRegisterStatus.FAILED);
        assertThat(reloadedPhoto.getFailureCode()).isEqualTo("FRS_TIMEOUT");

        FacePhotoRegisterTask message = readTask(registerDlqQueue);
        assertThat(message.retryCount()).isEqualTo(FacePhotoRegisterConsumer.MAX_RETRIES);
    }

    private FacePhoto createPendingPhoto(String username) throws Exception {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash("password123"));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        byte[] content = pngBytes();
        FacePhoto photo = new FacePhoto();
        photo.setId(UUID.randomUUID());
        photo.setUserId(user.getId());
        photo.setCreatedByUserId(user.getId());
        photo.setDetectStatus(FaceDetectStatus.PENDING);
        photo.setRegisterStatus(FaceRegisterStatus.PENDING);
        photo.setEnabled(true);
        String objectKey = objectKeyStrategy.facePhotoKey(user.getId(), photo.getId());
        HuaweiObsStorageService.StoredObject storedObject = storageService.upload(objectKey, content, "image/png");
        photo.setObsBucket(storedObject.bucket());
        photo.setObsRegion(storedObject.region());
        photo.setObsObjectKey(storedObject.objectKey());
        photo.setContentType(storedObject.contentType());
        photo.setSizeBytes(storedObject.sizeBytes());
        photo.setSha256("hash");
        photo.setStorageProvider(storedObject.storageProvider());
        return facePhotoRepository.save(photo);
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return outputStream.toByteArray();
    }

    private FacePhotoRegisterTask readTask(String queue) throws Exception {
        Message message = rabbitTemplate.receive(queue, 1000);
        assertThat(message).isNotNull();
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(message.getBody()))) {
            return (FacePhotoRegisterTask) inputStream.readObject();
        }
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        @Primary
        StubFaceRecognitionProvider stubFaceRecognitionProvider() {
            return new StubFaceRecognitionProvider();
        }
    }

    static class StubFaceRecognitionProvider implements FaceRecognitionProvider {

        private final Deque<Object> detectResponses = new ArrayDeque<>();
        private final Deque<Object> enrollResponses = new ArrayDeque<>();

        void reset() {
            detectResponses.clear();
            enrollResponses.clear();
        }

        void enqueueDetectResult(DetectFaceResult result) {
            detectResponses.addLast(result);
        }

        void enqueueDetectException(RuntimeException exception) {
            detectResponses.addLast(exception);
        }

        void enqueueEnrollResult(EnrollFaceResult result) {
            enrollResponses.addLast(result);
        }

        @Override
        public DetectFaceResult detectFace(byte[] imageBytes) {
            Object next = detectResponses.removeFirst();
            if (next instanceof RuntimeException exception) {
                throw exception;
            }
            return (DetectFaceResult) next;
        }

        @Override
        public EnrollFaceResult enrollFace(String externalImageId, Map<String, String> externalFields, byte[] imageBytes) {
            Object next = enrollResponses.removeFirst();
            if (next instanceof RuntimeException exception) {
                throw exception;
            }
            return (EnrollFaceResult) next;
        }

        @Override
        public SearchFaceResult searchFace(byte[] imageBytes, int maxCandidates) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompareFaceResult compareFace(byte[] imageBytes, String faceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteFace(String faceId) {
        }
    }
}
