package com.facecheck.face.service;

import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.face.api.dto.FacePhotoSummaryResponse;
import com.facecheck.face.messaging.FacePhotoRegisterPublisher;
import com.facecheck.face.model.FaceDetectStatus;
import com.facecheck.face.model.FacePhoto;
import com.facecheck.face.model.FaceRegisterStatus;
import com.facecheck.face.repo.FacePhotoRepository;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.storage.HuaweiObsStorageService;
import com.facecheck.storage.ObjectKeyStrategy;
import com.facecheck.storage.PreviewUrlService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FacePhotoService {

    private static final long MAX_FACE_PHOTO_COUNT = 5;

    private final FacePhotoRepository facePhotoRepository;
    private final UserRepository userRepository;
    private final CurrentUserAccess currentUserAccess;
    private final FaceImageValidationService faceImageValidationService;
    private final HuaweiObsStorageService storageService;
    private final ObjectKeyStrategy objectKeyStrategy;
    private final FacePhotoRegisterPublisher registerPublisher;
    private final PreviewUrlService previewUrlService;

    public FacePhotoService(
            FacePhotoRepository facePhotoRepository,
            UserRepository userRepository,
            CurrentUserAccess currentUserAccess,
            FaceImageValidationService faceImageValidationService,
            HuaweiObsStorageService storageService,
            ObjectKeyStrategy objectKeyStrategy,
            FacePhotoRegisterPublisher registerPublisher,
            PreviewUrlService previewUrlService
    ) {
        this.facePhotoRepository = facePhotoRepository;
        this.userRepository = userRepository;
        this.currentUserAccess = currentUserAccess;
        this.faceImageValidationService = faceImageValidationService;
        this.storageService = storageService;
        this.objectKeyStrategy = objectKeyStrategy;
        this.registerPublisher = registerPublisher;
        this.previewUrlService = previewUrlService;
    }

    @Transactional
    public FacePhotoSummaryResponse uploadCurrentUserPhoto(Authentication authentication, MultipartFile file) {
        UUID userId = currentUserAccess.requireUserId(authentication);
        return upload(userId, userId, file);
    }

    @Transactional
    public FacePhotoSummaryResponse uploadUserPhoto(UUID userId, Authentication authentication, MultipartFile file) {
        UUID actorUserId = authentication == null ? userId : currentUserAccess.requireUserId(authentication);
        return upload(userId, actorUserId, file);
    }

    private FacePhotoSummaryResponse upload(UUID userId, UUID actorUserId, MultipartFile file) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "User does not exist"));

        if (facePhotoRepository.countByUserIdAndEnabledTrue(userId) >= MAX_FACE_PHOTO_COUNT) {
            throw new BusinessException(ErrorCode.FACE_PHOTO_LIMIT_REACHED);
        }

        FaceImageValidationService.ValidatedImage validatedImage = faceImageValidationService.validate(file);
        FacePhoto photo = new FacePhoto();
        photo.setId(UUID.randomUUID());
        photo.setUserId(userId);
        photo.setCreatedByUserId(actorUserId);
        photo.setDetectStatus(FaceDetectStatus.PENDING);
        photo.setRegisterStatus(FaceRegisterStatus.PENDING);
        photo.setEnabled(true);

        String objectKey = objectKeyStrategy.facePhotoKey(userId, photo.getId());
        HuaweiObsStorageService.StoredObject storedObject =
                storageService.upload(objectKey, validatedImage.content(), validatedImage.contentType());

        photo.setObsBucket(storedObject.bucket());
        photo.setObsRegion(storedObject.region());
        photo.setObsObjectKey(storedObject.objectKey());
        photo.setContentType(storedObject.contentType());
        photo.setSizeBytes(storedObject.sizeBytes());
        photo.setSha256(validatedImage.sha256());
        photo.setStorageProvider(storedObject.storageProvider());
        FacePhoto savedPhoto = facePhotoRepository.saveAndFlush(photo);

        try {
            registerPublisher.publish(savedPhoto.getId(), userId);
        } catch (RuntimeException exception) {
            storageService.delete(objectKey);
            throw exception;
        }

        return FacePhotoSummaryResponse.from(savedPhoto, previewUrlService.previewUrl(savedPhoto));
    }
}
