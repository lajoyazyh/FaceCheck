package com.facecheck.face.service;

import com.facecheck.admin.service.AuditLogService;
import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.face.messaging.FacePhotoDeleteCompensationTask;
import com.facecheck.face.messaging.RetryableFacePhotoException;
import com.facecheck.face.model.FacePhoto;
import com.facecheck.face.model.FaceRegisterStatus;
import com.facecheck.face.model.HuaweiFaceRef;
import com.facecheck.face.model.HuaweiFaceRefStatus;
import com.facecheck.face.repo.FacePhotoRepository;
import com.facecheck.face.repo.HuaweiFaceRefRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FacePhotoDeletionService {

    private final FacePhotoRepository facePhotoRepository;
    private final HuaweiFaceRefRepository huaweiFaceRefRepository;
    private final CurrentUserAccess currentUserAccess;
    private final FaceRecognitionProvider faceRecognitionProvider;
    private final com.facecheck.storage.HuaweiObsStorageService storageService;
    private final RabbitTemplate rabbitTemplate;
    private final String deleteQueue;
    private final AuditLogService auditLogService;

    public FacePhotoDeletionService(
            FacePhotoRepository facePhotoRepository,
            HuaweiFaceRefRepository huaweiFaceRefRepository,
            CurrentUserAccess currentUserAccess,
            FaceRecognitionProvider faceRecognitionProvider,
            com.facecheck.storage.HuaweiObsStorageService storageService,
            RabbitTemplate rabbitTemplate,
            @Value("${facecheck.messaging.face-photo.delete-queue}") String deleteQueue,
            AuditLogService auditLogService
    ) {
        this.facePhotoRepository = facePhotoRepository;
        this.huaweiFaceRefRepository = huaweiFaceRefRepository;
        this.currentUserAccess = currentUserAccess;
        this.faceRecognitionProvider = faceRecognitionProvider;
        this.storageService = storageService;
        this.rabbitTemplate = rabbitTemplate;
        this.deleteQueue = deleteQueue;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void deleteCurrentUserPhoto(Authentication authentication, UUID photoId) {
        UUID userId = currentUserAccess.requireUserId(authentication);
        deletePhoto(loadActivePhoto(photoId, userId));
    }

    @Transactional
    public void deleteUserPhoto(UUID userId, UUID photoId) {
        deletePhoto(loadActivePhoto(photoId, userId));
        auditLogService.recordCurrentActor(
                "ADMIN_FACE_PHOTO_DELETE",
                "FACE_PHOTO",
                photoId,
                "Administrator deleted a managed user's face photo.",
                java.util.Map.of("userId", userId.toString())
        );
    }

    @Transactional
    public void compensateDeletion(UUID userId, UUID photoId) {
        FacePhoto photo = facePhotoRepository.findById(photoId)
                .filter(saved -> saved.getUserId().equals(userId))
                .orElseThrow(() -> new RetryableFacePhotoException("DELETE_COMPENSATION_FAILED", "Face photo does not exist"));
        deleteExternalArtifacts(photo);
    }

    private void deletePhoto(FacePhoto photo) {
        photo.setEnabled(false);
        photo.setRegisterStatus(FaceRegisterStatus.DELETE_PENDING);
        photo.setFailureCode(null);
        photo.setFailureReason(null);
        facePhotoRepository.save(photo);

        try {
            deleteExternalArtifacts(photo);
        } catch (RetryableFacePhotoException exception) {
            markDeleteFailed(photo, exception.code(), exception.getMessage());
            rabbitTemplate.convertAndSend(deleteQueue, new FacePhotoDeleteCompensationTask(photo.getId(), photo.getUserId(), 0));
        }
    }

    private void deleteExternalArtifacts(FacePhoto photo) {
        List<HuaweiFaceRef> refs = huaweiFaceRefRepository.findAllByFacePhotoId(photo.getId());
        try {
            storageService.delete(photo.getObsObjectKey());
            for (HuaweiFaceRef ref : refs) {
                if (ref.getStatus() == HuaweiFaceRefStatus.ACTIVE
                        || ref.getStatus() == HuaweiFaceRefStatus.DELETE_PENDING
                        || ref.getStatus() == HuaweiFaceRefStatus.DELETE_FAILED) {
                    faceRecognitionProvider.deleteFace(ref.getFrsFaceId());
                }
                ref.setStatus(HuaweiFaceRefStatus.DELETED);
            }
            huaweiFaceRefRepository.saveAll(refs);
            photo.setRegisterStatus(FaceRegisterStatus.DELETED);
            photo.setFailureCode(null);
            photo.setFailureReason(null);
            facePhotoRepository.save(photo);
        } catch (RuntimeException exception) {
            for (HuaweiFaceRef ref : refs) {
                ref.setStatus(HuaweiFaceRefStatus.DELETE_FAILED);
            }
            huaweiFaceRefRepository.saveAll(refs);
            throw new RetryableFacePhotoException("DELETE_COMPENSATION_FAILED", "Failed to delete external face data", exception);
        }
    }

    private void markDeleteFailed(FacePhoto photo, String code, String message) {
        photo.setRegisterStatus(FaceRegisterStatus.DELETE_FAILED);
        photo.setFailureCode(code);
        photo.setFailureReason(message);
        facePhotoRepository.save(photo);
    }

    private FacePhoto loadActivePhoto(UUID photoId, UUID userId) {
        return facePhotoRepository.findByIdAndUserIdAndEnabledTrue(photoId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Face photo does not exist"));
    }
}
