package com.facecheck.admin.service;

import com.facecheck.admin.api.dto.AdminCheckinAttemptResponse;
import com.facecheck.admin.api.dto.ReviewCheckinAttemptRequest;
import com.facecheck.auth.security.AuthenticatedUser;
import com.facecheck.auth.security.CurrentUserAccess;
import com.facecheck.checkin.model.AttendanceCheckinAttempt;
import com.facecheck.checkin.model.AttendanceRecordSource;
import com.facecheck.checkin.model.CheckinStatus;
import com.facecheck.checkin.repo.AttendanceCheckinAttemptRepository;
import com.facecheck.checkin.service.AttendanceRecordService;
import com.facecheck.checkin.service.CheckinRecognitionService;
import com.facecheck.common.error.BusinessException;
import com.facecheck.common.error.ErrorCode;
import com.facecheck.identity.model.User;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.session.model.AttendanceSession;
import com.facecheck.session.repo.AttendanceSessionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckinReviewService {

    private final AttendanceCheckinAttemptRepository attendanceCheckinAttemptRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final UserRepository userRepository;
    private final AttendanceRecordService attendanceRecordService;
    private final CheckinRecognitionService checkinRecognitionService;
    private final CurrentUserAccess currentUserAccess;
    private final AuditLogService auditLogService;

    public CheckinReviewService(
            AttendanceCheckinAttemptRepository attendanceCheckinAttemptRepository,
            AttendanceSessionRepository attendanceSessionRepository,
            UserRepository userRepository,
            AttendanceRecordService attendanceRecordService,
            CheckinRecognitionService checkinRecognitionService,
            CurrentUserAccess currentUserAccess,
            AuditLogService auditLogService
    ) {
        this.attendanceCheckinAttemptRepository = attendanceCheckinAttemptRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.userRepository = userRepository;
        this.attendanceRecordService = attendanceRecordService;
        this.checkinRecognitionService = checkinRecognitionService;
        this.currentUserAccess = currentUserAccess;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<AdminCheckinAttemptResponse> listAttempts(
            CheckinStatus status,
            String resultCode,
            UUID sessionId,
            Boolean reviewed
    ) {
        List<AttendanceCheckinAttempt> attempts = attendanceCheckinAttemptRepository.findAll(
                byStatus(status)
                        .and(byResultCode(resultCode))
                        .and(bySessionId(sessionId))
                        .and(byReviewed(reviewed))
                        .and(defaultAbnormalFilter(status, resultCode)),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Map<UUID, String> sessionNames = attendanceSessionRepository.findAllById(
                        attempts.stream().map(AttendanceCheckinAttempt::getSessionId).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(AttendanceSession::getId, AttendanceSession::getName));

        Map<UUID, User> users = userRepository.findAllById(
                        attempts.stream()
                                .map(AttendanceCheckinAttempt::getMatchedUserId)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, java.util.function.Function.identity()));

        return attempts.stream()
                .map(attempt -> toResponse(attempt, sessionNames.get(attempt.getSessionId()), users.get(attempt.getMatchedUserId())))
                .toList();
    }

    @Transactional
    public AdminCheckinAttemptResponse reviewAttempt(UUID attemptId, ReviewCheckinAttemptRequest request) {
        if ((request.note() == null || request.note().isBlank()) && request.reviewed() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Provide a review note or reviewed flag.");
        }

        AttendanceCheckinAttempt attempt = loadAttempt(attemptId);
        AuthenticatedUser actor = currentActor();

        if (request.note() != null && !request.note().isBlank()) {
            attempt.setReviewNote(mergeNote(attempt.getReviewNote(), request.note().trim()));
        }
        if (request.reviewed() != null) {
            attempt.setReviewed(request.reviewed());
        }
        attempt.setReviewedByUserId(actor.userId());
        attempt.setReviewedAt(Instant.now());

        AttendanceCheckinAttempt saved = attendanceCheckinAttemptRepository.save(attempt);
        auditLogService.recordCurrentActor(
                "CHECKIN_ATTEMPT_REVIEW",
                "ATTENDANCE_CHECKIN_ATTEMPT",
                attemptId,
                "Administrator reviewed an abnormal check-in attempt.",
                Map.of("reviewed", saved.isReviewed(), "resultCode", saved.getResultCode())
        );
        return toResponse(saved);
    }

    @Transactional
    public AdminCheckinAttemptResponse retryAttempt(UUID attemptId) {
        AttendanceCheckinAttempt attempt = loadAttempt(attemptId);
        if (attempt.getStatus() == CheckinStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Processing attempts cannot be retried yet.");
        }

        AuthenticatedUser actor = currentActor();
        CheckinRecognitionService.RecognitionOutcome recognitionOutcome = checkinRecognitionService.recognize(attempt);
        attempt.setRetryCount(attempt.getRetryCount() + 1);
        attempt.setReviewed(true);
        attempt.setReviewedByUserId(actor.userId());
        attempt.setReviewedAt(Instant.now());

        if (recognitionOutcome.matched()) {
            AttendanceRecordService.RecordOutcome recordOutcome = attendanceRecordService.persist(
                    attempt.getSessionId(),
                    recognitionOutcome.matchedUserId(),
                    attempt.getId(),
                    recognitionOutcome.similarity(),
                    AttendanceRecordSource.ADMIN_RETRY
            );
            if (recordOutcome.created()) {
                attempt.setStatus(CheckinStatus.SUCCESS);
                attempt.setResultCode("SUCCESS");
                attempt.setFailureReason(null);
            } else {
                attempt.setStatus(CheckinStatus.DUPLICATE_CHECKIN);
                attempt.setResultCode("DUPLICATE_CHECKIN");
                attempt.setFailureReason("This user has already checked in for the session.");
            }
            attempt.setMatchedUserId(recognitionOutcome.matchedUserId());
            attempt.setMatchedFaceId(recognitionOutcome.matchedFaceId());
            attempt.setSimilarity(recognitionOutcome.similarity());
            attempt.setFrsRequestId(recognitionOutcome.frsRequestId());
        } else {
            attempt.setStatus(CheckinStatus.FAILED);
            attempt.setResultCode(recognitionOutcome.resultCode());
            attempt.setFailureReason(recognitionOutcome.resultMessage());
            attempt.setFrsRequestId(recognitionOutcome.frsRequestId());
        }

        AttendanceCheckinAttempt saved = attendanceCheckinAttemptRepository.save(attempt);
        auditLogService.recordCurrentActor(
                "CHECKIN_ATTEMPT_RETRY",
                "ATTENDANCE_CHECKIN_ATTEMPT",
                attemptId,
                "Administrator retried an abnormal check-in attempt.",
                Map.of("status", saved.getStatus().name(), "resultCode", saved.getResultCode(), "retryCount", saved.getRetryCount())
        );
        return toResponse(saved);
    }

    private AttendanceCheckinAttempt loadAttempt(UUID attemptId) {
        return attendanceCheckinAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Check-in attempt does not exist"));
    }

    private AdminCheckinAttemptResponse toResponse(AttendanceCheckinAttempt attempt) {
        AttendanceSession session = attendanceSessionRepository.findById(attempt.getSessionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Attendance session does not exist"));
        User matchedUser = attempt.getMatchedUserId() == null ? null : userRepository.findById(attempt.getMatchedUserId()).orElse(null);
        return toResponse(attempt, session.getName(), matchedUser);
    }

    private AdminCheckinAttemptResponse toResponse(AttendanceCheckinAttempt attempt, String sessionName, User matchedUser) {
        return new AdminCheckinAttemptResponse(
                attempt.getId(),
                attempt.getSessionId(),
                sessionName == null ? "Unknown Session" : sessionName,
                attempt.getStatus(),
                attempt.getResultCode(),
                message(attempt),
                attempt.getCreatedAt(),
                attempt.getUpdatedAt(),
                attempt.getMatchedUserId(),
                mask(matchedUser),
                attempt.getSimilarity(),
                attempt.isReviewed(),
                attempt.getReviewNote(),
                attempt.getReviewedAt(),
                attempt.getReviewedByUserId(),
                attempt.getRetryCount()
        );
    }

    private Specification<AttendanceCheckinAttempt> byStatus(CheckinStatus status) {
        return (root, query, builder) -> status == null
                ? builder.conjunction()
                : builder.equal(root.get("status"), status);
    }

    private Specification<AttendanceCheckinAttempt> byResultCode(String resultCode) {
        return (root, query, builder) -> resultCode == null || resultCode.isBlank()
                ? builder.conjunction()
                : builder.equal(root.get("resultCode"), resultCode.trim());
    }

    private Specification<AttendanceCheckinAttempt> bySessionId(UUID sessionId) {
        return (root, query, builder) -> sessionId == null
                ? builder.conjunction()
                : builder.equal(root.get("sessionId"), sessionId);
    }

    private Specification<AttendanceCheckinAttempt> byReviewed(Boolean reviewed) {
        return (root, query, builder) -> reviewed == null
                ? builder.conjunction()
                : builder.equal(root.get("reviewed"), reviewed);
    }

    private Specification<AttendanceCheckinAttempt> defaultAbnormalFilter(CheckinStatus status, String resultCode) {
        return (root, query, builder) -> (status != null || (resultCode != null && !resultCode.isBlank()))
                ? builder.conjunction()
                : builder.notEqual(root.get("status"), CheckinStatus.SUCCESS);
    }

    private String message(AttendanceCheckinAttempt attempt) {
        if (attempt.getFailureReason() != null && !attempt.getFailureReason().isBlank()) {
            return attempt.getFailureReason();
        }
        return switch (attempt.getResultCode()) {
            case "SUCCESS" -> "Check-in completed successfully.";
            case "DUPLICATE_CHECKIN" -> "This user has already checked in for the session.";
            case "MANUAL_REVIEW" -> "The recognition result requires manual review.";
            case "LOW_CONFIDENCE" -> "The matched face confidence is below the acceptance threshold.";
            case "NO_MATCH" -> "No enrolled face matched the submitted image.";
            case "NO_FACE" -> "No face was detected in the submitted image.";
            case "MULTIPLE_FACES" -> "Multiple faces were detected in the submitted image.";
            case "FRS_TIMEOUT" -> "The face recognition service timed out.";
            case "FRS_RATE_LIMITED" -> "The face recognition service rate-limited the request.";
            case "FRS_ERROR" -> "The face recognition service returned an unexpected error.";
            default -> "The check-in request failed.";
        };
    }

    private String mask(User user) {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            return null;
        }
        String username = user.getUsername();
        if (username.length() <= 2) {
            return username.charAt(0) + "*";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }

    private String mergeNote(String existing, String incoming) {
        if (existing == null || existing.isBlank()) {
            return incoming;
        }
        return existing + "\n" + incoming;
    }

    private AuthenticatedUser currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return currentUserAccess.require(authentication);
    }
}
