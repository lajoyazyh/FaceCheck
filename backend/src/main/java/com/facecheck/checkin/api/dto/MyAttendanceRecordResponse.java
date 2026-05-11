package com.facecheck.checkin.api.dto;

import com.facecheck.checkin.model.AttendanceRecordStatus;
import java.time.Instant;
import java.util.UUID;

public record MyAttendanceRecordResponse(
        UUID recordId,
        UUID sessionId,
        String sessionName,
        Instant checkinTime,
        AttendanceRecordStatus status,
        String message
) {
}
