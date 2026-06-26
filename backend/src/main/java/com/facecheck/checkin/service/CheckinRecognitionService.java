package com.facecheck.checkin.service;

import com.facecheck.admin.service.ExternalCallAuditService;
import com.facecheck.checkin.config.CheckinProperties;
import com.facecheck.checkin.model.AttendanceCheckinAttempt;
import com.facecheck.face.FaceRecognitionProvider;
import com.facecheck.face.repo.HuaweiFaceRefRepository;
import com.facecheck.infrastructure.config.HuaweiCloudProperties;
import com.facecheck.infrastructure.huawei.HuaweiFrsException;
import com.facecheck.identity.model.User;
import com.facecheck.identity.repo.UserRepository;
import com.facecheck.storage.HuaweiObsStorageService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CheckinRecognitionService {

    private final HuaweiObsStorageService storageService;
    private final FaceRecognitionProvider faceRecognitionProvider;
    private final HuaweiFaceRefRepository huaweiFaceRefRepository;
    private final UserRepository userRepository;
    private final CheckinProperties checkinProperties;
    private final HuaweiCloudProperties huaweiCloudProperties;
    private final ExternalCallAuditService externalCallAuditService;

    public CheckinRecognitionService(
            HuaweiObsStorageService storageService,
            FaceRecognitionProvider faceRecognitionProvider,
            HuaweiFaceRefRepository huaweiFaceRefRepository,
            UserRepository userRepository,
            CheckinProperties checkinProperties,
            HuaweiCloudProperties huaweiCloudProperties,
            ExternalCallAuditService externalCallAuditService
    ) {
        this.storageService = storageService;
        this.faceRecognitionProvider = faceRecognitionProvider;
        this.huaweiFaceRefRepository = huaweiFaceRefRepository;
        this.userRepository = userRepository;
        this.checkinProperties = checkinProperties;
        this.huaweiCloudProperties = huaweiCloudProperties;
        this.externalCallAuditService = externalCallAuditService;
    }

    public RecognitionOutcome recognize(AttendanceCheckinAttempt attempt) {
        byte[] content = storageService.read(attempt.getObsObjectKey());

        FaceRecognitionProvider.DetectFaceResult detectResult;
        long detectStart = System.nanoTime();
        try {
            detectResult = faceRecognitionProvider.detectFace(content);
            externalCallAuditService.recordSuccess(
                    "HUAWEI_FRS",
                    "DetectFace",
                    attempt.getId(),
                    detectResult.requestId(),
                    elapsedMs(detectStart)
            );
        } catch (RuntimeException exception) {
            return externalFailure("DetectFace", attempt, exception);
        }

        if (!detectResult.acceptable()) {
            String resultCode = normalizeDetectFailure(detectResult);
            return RecognitionOutcome.failed(resultCode, resultMessage(resultCode), detectResult.requestId());
        }

        FaceRecognitionProvider.SearchFaceResult searchResult;
        long searchStart = System.nanoTime();
        try {
            searchResult = faceRecognitionProvider.searchFace(content, checkinProperties.getSearchMaxCandidates());
            externalCallAuditService.recordSuccess(
                    "HUAWEI_FRS",
                    "SearchFace",
                    attempt.getId(),
                    searchResult.requestId(),
                    elapsedMs(searchStart)
            );
        } catch (RuntimeException exception) {
            return externalFailure("SearchFace", attempt, exception);
        }

        List<FaceRecognitionProvider.SearchCandidate> candidates = searchResult.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return RecognitionOutcome.failed("NO_MATCH", resultMessage("NO_MATCH"), searchResult.requestId());
        }

        FaceRecognitionProvider.SearchCandidate candidate = candidates.getFirst();
        UUID matchedUserId = resolveUserId(candidate);
        if (matchedUserId == null) {
            return RecognitionOutcome.failed("MANUAL_REVIEW", resultMessage("MANUAL_REVIEW"), searchResult.requestId());
        }

        FaceRecognitionProvider.CompareFaceResult compareResult;
        long compareStart = System.nanoTime();
        try {
            compareResult = faceRecognitionProvider.compareFace(content, candidate.faceId());
            externalCallAuditService.recordSuccess(
                    "HUAWEI_FRS",
                    "CompareFace",
                    attempt.getId(),
                    compareResult.requestId(),
                    elapsedMs(compareStart)
            );
        } catch (RuntimeException exception) {
            return externalFailure("CompareFace", attempt, exception);
        }

        if (!compareResult.matched() || compareResult.similarity() < huaweiCloudProperties.getSimilarityThreshold()) {
            return RecognitionOutcome.failed("LOW_CONFIDENCE", resultMessage("LOW_CONFIDENCE"), compareResult.requestId());
        }

        return RecognitionOutcome.success(
                matchedUserId,
                candidate.faceId(),
                compareResult.similarity(),
                compareResult.requestId()
        );
    }

    private UUID resolveUserId(FaceRecognitionProvider.SearchCandidate candidate) {
        Map<String, String> externalFields = candidate.externalFields();
        if (externalFields != null && externalFields.containsKey("userId")) {
            try {
                UUID userId = UUID.fromString(externalFields.get("userId"));
                if (isActiveUser(userId)) {
                    return userId;
                }
                return null;
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return huaweiFaceRefRepository.findByFrsFaceId(candidate.faceId())
                .map(ref -> ref.getUserId())
                .filter(this::isActiveUser)
                .orElse(null);
    }

    private boolean isActiveUser(UUID userId) {
        return userRepository.findById(userId)
                .map(User::isActive)
                .orElse(false);
    }

    private RecognitionOutcome externalFailure(String operation, AttendanceCheckinAttempt attempt, RuntimeException exception) {
        String resultCode = classifyExternalError(exception);
        externalCallAuditService.recordFailure(
                "HUAWEI_FRS",
                operation,
                attempt.getId(),
                requestId(exception),
                resultCode,
                0L,
                exception
        );
        return RecognitionOutcome.failed(resultCode, resultMessage(resultCode), requestId(exception));
    }

    private String normalizeDetectFailure(FaceRecognitionProvider.DetectFaceResult detectResult) {
        String failureCode = detectResult.failureCode();
        if ("NO_FACE".equals(failureCode)) {
            return "NO_FACE";
        }
        if ("MULTIPLE_FACES".equals(failureCode)) {
            return "MULTIPLE_FACES";
        }
        if ("LOW_QUALITY_IMAGE".equals(failureCode)) {
            return "INVALID_IMAGE";
        }
        if (detectResult.faceCount() > 1) {
            return "MULTIPLE_FACES";
        }
        return failureCode == null ? "INVALID_IMAGE" : failureCode;
    }

    private String classifyExternalError(RuntimeException exception) {
        if (exception instanceof HuaweiFrsException frsException) {
            return frsException.code();
        }
        String message = exception.getMessage() == null ? "" : exception.getMessage().toUpperCase();
        if (message.contains("TIMEOUT")) {
            return "FRS_TIMEOUT";
        }
        if (message.contains("RATE") || message.contains("TOO MANY REQUEST")) {
            return "FRS_RATE_LIMITED";
        }
        return "FRS_ERROR";
    }

    private String requestId(RuntimeException exception) {
        if (exception instanceof HuaweiFrsException frsException) {
            return frsException.requestId();
        }
        return null;
    }

    private String resultMessage(String resultCode) {
        return switch (resultCode) {
            case "NO_FACE" -> "No face was detected in the submitted image.";
            case "MULTIPLE_FACES" -> "Multiple faces were detected in the submitted image.";
            case "INVALID_IMAGE" -> "The submitted image is invalid for face recognition.";
            case "LOW_CONFIDENCE" -> "The matched face confidence is below the acceptance threshold.";
            case "NO_MATCH" -> "No enrolled face matched the submitted image.";
            case "FRS_TIMEOUT" -> "The face recognition service timed out.";
            case "FRS_RATE_LIMITED" -> "The face recognition service rate-limited the request.";
            case "MANUAL_REVIEW" -> "The recognition result requires manual review.";
            default -> "The face recognition request failed.";
        };
    }

    private long elapsedMs(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    public record RecognitionOutcome(
            boolean matched,
            UUID matchedUserId,
            String matchedFaceId,
            Double similarity,
            String resultCode,
            String resultMessage,
            String frsRequestId
    ) {

        public static RecognitionOutcome success(UUID matchedUserId, String matchedFaceId, Double similarity, String frsRequestId) {
            return new RecognitionOutcome(true, matchedUserId, matchedFaceId, similarity, "SUCCESS", null, frsRequestId);
        }

        public static RecognitionOutcome failed(String resultCode, String resultMessage, String frsRequestId) {
            return new RecognitionOutcome(false, null, null, null, resultCode, resultMessage, frsRequestId);
        }
    }
}
