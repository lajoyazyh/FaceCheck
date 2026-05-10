package com.facecheck.face.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "face_photo")
public class FacePhoto {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

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
    @Column(name = "detect_status", nullable = false, length = 32)
    private FaceDetectStatus detectStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "register_status", nullable = false, length = 32)
    private FaceRegisterStatus registerStatus;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

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

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
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

    public FaceDetectStatus getDetectStatus() {
        return detectStatus;
    }

    public void setDetectStatus(FaceDetectStatus detectStatus) {
        this.detectStatus = detectStatus;
    }

    public FaceRegisterStatus getRegisterStatus() {
        return registerStatus;
    }

    public void setRegisterStatus(FaceRegisterStatus registerStatus) {
        this.registerStatus = registerStatus;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
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
        if (detectStatus == null) {
            detectStatus = FaceDetectStatus.PENDING;
        }
        if (registerStatus == null) {
            registerStatus = FaceRegisterStatus.PENDING;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
