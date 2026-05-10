package com.facecheck.face.messaging;

import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.face.model.FaceDetectStatus;
import com.facecheck.face.model.FacePhoto;
import com.facecheck.face.model.FaceRegisterStatus;
import com.facecheck.face.model.HuaweiFaceRef;
import com.facecheck.face.model.HuaweiFaceRefStatus;
import com.facecheck.face.repo.FacePhotoRepository;
import com.facecheck.face.repo.HuaweiFaceRefRepository;
import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.facecheck.storage.HuaweiObsStorageService;
import java.util.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FacePhotoRegisterConsumer {

    public static final int MAX_RETRIES = 3;

    private final FacePhotoRepository facePhotoRepository;
    private final HuaweiFaceRefRepository huaweiFaceRefRepository;
    private final HuaweiObsStorageService storageService;
    private final FaceRecognitionProvider faceRecognitionProvider;
    private final HuaweiCloudProperties huaweiCloudProperties;
    private final RabbitTemplate rabbitTemplate;
    private final String retryQueue;
    private final String dlqQueue;

    public FacePhotoRegisterConsumer(
            FacePhotoRepository facePhotoRepository,
            HuaweiFaceRefRepository huaweiFaceRefRepository,
            HuaweiObsStorageService storageService,
            FaceRecognitionProvider faceRecognitionProvider,
            HuaweiCloudProperties huaweiCloudProperties,
            RabbitTemplate rabbitTemplate,
            @Value("${facecheck.messaging.face-photo.register-retry-queue}") String retryQueue,
            @Value("${facecheck.messaging.face-photo.register-dlq-queue}") String dlqQueue
    ) {
        this.facePhotoRepository = facePhotoRepository;
        this.huaweiFaceRefRepository = huaweiFaceRefRepository;
        this.storageService = storageService;
        this.faceRecognitionProvider = faceRecognitionProvider;
        this.huaweiCloudProperties = huaweiCloudProperties;
        this.rabbitTemplate = rabbitTemplate;
        this.retryQueue = retryQueue;
        this.dlqQueue = dlqQueue;
    }

    @RabbitListener(queues = "${facecheck.messaging.face-photo.register-queue}")
    public void receive(FacePhotoRegisterTask task) {
        consume(task);
    }

    @Transactional
    public void consume(FacePhotoRegisterTask task) {
        FacePhoto photo = facePhotoRepository.findById(task.photoId())
                .filter(saved -> saved.getUserId().equals(task.userId()))
                .orElse(null);
        if (photo == null || !photo.isEnabled() || photo.getRegisterStatus() == FaceRegisterStatus.DELETED) {
            return;
        }

        try {
            byte[] content = storageService.read(photo.getObsObjectKey());
            FaceRecognitionProvider.DetectFaceResult detectResult = faceRecognitionProvider.detectFace(content);
            if (!detectResult.acceptable()) {
                markPermanentFailure(photo, detectResult.failureCode(), failureReason(detectResult.failureCode()));
                return;
            }

            photo.setDetectStatus(FaceDetectStatus.PASSED);
            photo.setFailureCode(null);
            photo.setFailureReason(null);

            Map<String, String> externalFields = Map.of(
                    "userId", photo.getUserId().toString(),
                    "photoId", photo.getId().toString()
            );
            FaceRecognitionProvider.EnrollFaceResult enrollResult =
                    faceRecognitionProvider.enrollFace(photo.getId().toString(), externalFields, content);

            HuaweiFaceRef ref = huaweiFaceRefRepository.findByFacePhotoId(photo.getId()).orElseGet(HuaweiFaceRef::new);
            ref.setUserId(photo.getUserId());
            ref.setFacePhotoId(photo.getId());
            ref.setFaceSetName(huaweiCloudProperties.getFaceSetName());
            ref.setFrsFaceId(enrollResult.faceId());
            ref.setExternalImageId(photo.getId().toString());
            ref.setExternalFields(externalFields);
            ref.setStatus(HuaweiFaceRefStatus.ACTIVE);
            huaweiFaceRefRepository.save(ref);

            photo.setRegisterStatus(FaceRegisterStatus.ACTIVE);
            facePhotoRepository.save(photo);
        } catch (RetryableFacePhotoException exception) {
            handleRetryableFailure(task, photo, exception);
        } catch (RuntimeException exception) {
            markPermanentFailure(photo, "FRS_ERROR", "Face registration failed.");
        }
    }

    private void handleRetryableFailure(FacePhotoRegisterTask task, FacePhoto photo, RetryableFacePhotoException exception) {
        photo.setFailureCode(exception.code());
        photo.setFailureReason(exception.getMessage());
        if (task.retryCount() >= MAX_RETRIES) {
            photo.setRegisterStatus(FaceRegisterStatus.FAILED);
            facePhotoRepository.save(photo);
            rabbitTemplate.convertAndSend(dlqQueue, task);
            return;
        }
        facePhotoRepository.save(photo);
        rabbitTemplate.convertAndSend(retryQueue, new FacePhotoRegisterTask(task.photoId(), task.userId(), task.retryCount() + 1));
    }

    private void markPermanentFailure(FacePhoto photo, String failureCode, String failureReason) {
        photo.setDetectStatus(FaceDetectStatus.FAILED);
        photo.setRegisterStatus(FaceRegisterStatus.FAILED);
        photo.setFailureCode(failureCode == null ? "INVALID_IMAGE" : failureCode);
        photo.setFailureReason(failureReason);
        facePhotoRepository.save(photo);
    }

    private String failureReason(String failureCode) {
        return switch (failureCode) {
            case "NO_FACE" -> "No face was detected in the uploaded image.";
            case "MULTIPLE_FACES" -> "Multiple faces were detected in the uploaded image.";
            case "LOW_QUALITY_IMAGE" -> "The uploaded image quality is too low for face registration.";
            default -> "The uploaded image failed face validation.";
        };
    }
}
