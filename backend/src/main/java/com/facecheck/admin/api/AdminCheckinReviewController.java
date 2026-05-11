package com.facecheck.admin.api;

import com.facecheck.admin.api.dto.AdminCheckinAttemptResponse;
import com.facecheck.admin.api.dto.ReviewCheckinAttemptRequest;
import com.facecheck.admin.service.CheckinReviewService;
import com.facecheck.auth.security.AdminOnly;
import com.facecheck.checkin.model.CheckinStatus;
import com.facecheck.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AdminOnly
@RequestMapping("/api/admin/checkin-attempts")
public class AdminCheckinReviewController {

    private final CheckinReviewService checkinReviewService;

    public AdminCheckinReviewController(CheckinReviewService checkinReviewService) {
        this.checkinReviewService = checkinReviewService;
    }

    @GetMapping
    public ApiResponse<List<AdminCheckinAttemptResponse>> listAttempts(
            @RequestParam(value = "status", required = false) CheckinStatus status,
            @RequestParam(value = "resultCode", required = false) String resultCode,
            @RequestParam(value = "sessionId", required = false) UUID sessionId,
            @RequestParam(value = "reviewed", required = false) Boolean reviewed
    ) {
        return ApiResponse.success(checkinReviewService.listAttempts(status, resultCode, sessionId, reviewed));
    }

    @PostMapping("/{attemptId}/review")
    public ApiResponse<AdminCheckinAttemptResponse> reviewAttempt(
            @PathVariable UUID attemptId,
            @Valid @RequestBody ReviewCheckinAttemptRequest request
    ) {
        return ApiResponse.success(checkinReviewService.reviewAttempt(attemptId, request));
    }

    @PostMapping("/{attemptId}/retry")
    public ResponseEntity<ApiResponse<AdminCheckinAttemptResponse>> retryAttempt(@PathVariable UUID attemptId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(checkinReviewService.retryAttempt(attemptId)));
    }
}
