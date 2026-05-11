package com.facecheck.checkin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "attendance_checkin_attempt",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_checkin_attempt_session_idempotency",
                columnNames = {"session_id", "idempotency_key"}
        )
)
public class AttendanceCheckinAttempt {

    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "obs_bucket", nullable = false, length = 128)
    private String obsBucket;

    @Column(name = "obs_region", nullable = false, length = 128)
    private String obsRegion;

    @Column(name = "obs_object_key", nullable = false, length = 255)
    private String obsObjectKey;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_provider", nullable = false, length = 64)
    private String storageProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckinStatus status;

    @Column(name = "result_code", nullable = false, length = 64)
    private String resultCode;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "frs_request_id", length = 128)
    private String frsRequestId;

    @Column(name = "matched_user_id")
    private UUID matchedUserId;

    @Column(name = "matched_face_id", length = 128)
    private String matchedFaceId;

    private Double similarity;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(nullable = false)
    private boolean reviewed;

    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getObsBucket() {
        return obsBucket;
    }

    public void setObsBucket(String obsBucket) {
        this.obsBucket = obsBucket;
    }

    public String getObsRegion() {
        return obsRegion;
    }

    public void setObsRegion(String obsRegion) {
        this.obsRegion = obsRegion;
    }

    public String getObsObjectKey() {
        return obsObjectKey;
    }

    public void setObsObjectKey(String obsObjectKey) {
        this.obsObjectKey = obsObjectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public CheckinStatus getStatus() {
        return status;
    }

    public void setStatus(CheckinStatus status) {
        this.status = status;
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getFrsRequestId() {
        return frsRequestId;
    }

    public void setFrsRequestId(String frsRequestId) {
        this.frsRequestId = frsRequestId;
    }

    public UUID getMatchedUserId() {
        return matchedUserId;
    }

    public void setMatchedUserId(UUID matchedUserId) {
        this.matchedUserId = matchedUserId;
    }

    public String getMatchedFaceId() {
        return matchedFaceId;
    }

    public void setMatchedFaceId(String matchedFaceId) {
        this.matchedFaceId = matchedFaceId;
    }

    public Double getSimilarity() {
        return similarity;
    }

    public void setSimilarity(Double similarity) {
        this.similarity = similarity;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public void setReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }

    public UUID getReviewedByUserId() {
        return reviewedByUserId;
    }

    public void setReviewedByUserId(UUID reviewedByUserId) {
        this.reviewedByUserId = reviewedByUserId;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(Instant reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = CheckinStatus.PROCESSING;
        }
        if (resultCode == null) {
            resultCode = "PROCESSING";
        }
        if (retryCount < 0) {
            retryCount = 0;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
