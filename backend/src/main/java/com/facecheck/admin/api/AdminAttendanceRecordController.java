package com.facecheck.admin.api;

import com.facecheck.admin.api.dto.AdminAttendanceRecordResponse;
import com.facecheck.admin.service.AdminAttendanceRecordQueryService;
import com.facecheck.auth.security.AdminOnly;
import com.facecheck.checkin.model.AttendanceRecordStatus;
import com.facecheck.common.api.ApiResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AdminOnly
@RequestMapping("/api/admin")
public class AdminAttendanceRecordController {

    private final AdminAttendanceRecordQueryService adminAttendanceRecordQueryService;

    public AdminAttendanceRecordController(AdminAttendanceRecordQueryService adminAttendanceRecordQueryService) {
        this.adminAttendanceRecordQueryService = adminAttendanceRecordQueryService;
    }

    @GetMapping("/attendance-records")
    public ApiResponse<List<AdminAttendanceRecordResponse>> listGlobalRecords(
            @RequestParam(value = "sessionId", required = false) UUID sessionId,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "status", required = false) AttendanceRecordStatus status
    ) {
        return ApiResponse.success(adminAttendanceRecordQueryService.listRecords(sessionId, userId, from, to, status));
    }

    @GetMapping("/sessions/{sessionId}/records")
    public ApiResponse<List<AdminAttendanceRecordResponse>> listSessionRecords(
            @PathVariable UUID sessionId,
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(value = "status", required = false) AttendanceRecordStatus status
    ) {
        return ApiResponse.success(adminAttendanceRecordQueryService.listRecords(sessionId, null, from, to, status));
    }
}
