package com.facecheck.face.messaging;

import java.io.Serializable;
import java.util.UUID;

public record FacePhotoDeleteCompensationTask(UUID photoId, UUID userId, int retryCount) implements Serializable {
}
