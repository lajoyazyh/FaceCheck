package com.facecheck.face.api.dto;

import com.facecheck.face.model.FacePhoto;
import com.facecheck.face.service.FacePhotoViewStatus;
import java.time.Instant;
import java.util.UUID;

public record FacePhotoSummaryResponse(
        UUID photoId,
        UUID userId,
        FacePhotoViewStatus status,
        String detectStatus,
        String registerStatus,
        String failureCode,
        String failureReason,
        String previewUrl,
        boolean enabled,
        Instant createdAt
) {

    public static FacePhotoSummaryResponse from(FacePhoto photo, String previewUrl) {
        return new FacePhotoSummaryResponse(
                photo.getId(),
                photo.getUserId(),
                FacePhotoViewStatus.from(photo),
                photo.getDetectStatus().name(),
                photo.getRegisterStatus().name(),
                photo.getFailureCode(),
                photo.getFailureReason(),
                previewUrl,
                photo.isEnabled(),
                photo.getCreatedAt()
        );
    }
}
