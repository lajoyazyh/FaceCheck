package com.facecheck.admin.api.dto;

import com.facecheck.checkin.model.AttendanceRecordStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminAttendanceRecordResponse(
        UUID recordId,
        UUID sessionId,
        String sessionName,
        UUID userId,
        String maskedUsername,
        Instant checkinTime,
        AttendanceRecordStatus status,
        Double similarity
) {
}
