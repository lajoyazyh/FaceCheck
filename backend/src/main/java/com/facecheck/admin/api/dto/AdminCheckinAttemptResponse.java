package com.facecheck.admin.api.dto;

import com.facecheck.checkin.model.CheckinStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminCheckinAttemptResponse(
        UUID attemptId,
        UUID sessionId,
        String sessionName,
        CheckinStatus status,
        String resultCode,
        String resultMessage,
        Instant createdAt,
        Instant updatedAt,
        UUID matchedUserId,
        String maskedUsername,
        Double similarity,
        boolean reviewed,
        String reviewNote,
        Instant reviewedAt,
        UUID reviewedByUserId,
        int retryCount
) {
}
