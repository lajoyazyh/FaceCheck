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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "huawei_face_ref")
public class HuaweiFaceRef {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "face_photo_id", nullable = false)
    private UUID facePhotoId;

    @Column(name = "face_set_name", nullable = false, length = 128)
    private String faceSetName;

    @Column(name = "frs_face_id", nullable = false, length = 128, unique = true)
    private String frsFaceId;

    @Column(name = "external_image_id", nullable = false, length = 128)
    private String externalImageId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_fields", nullable = false)
    private Map<String, String> externalFields = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private HuaweiFaceRefStatus status;

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

    public UUID getFacePhotoId() {
        return facePhotoId;
    }

    public void setFacePhotoId(UUID facePhotoId) {
        this.facePhotoId = facePhotoId;
    }

    public String getFaceSetName() {
        return faceSetName;
    }

    public void setFaceSetName(String faceSetName) {
        this.faceSetName = faceSetName;
    }

    public String getFrsFaceId() {
        return frsFaceId;
    }

    public void setFrsFaceId(String frsFaceId) {
        this.frsFaceId = frsFaceId;
    }

    public String getExternalImageId() {
        return externalImageId;
    }

    public void setExternalImageId(String externalImageId) {
        this.externalImageId = externalImageId;
    }

    public Map<String, String> getExternalFields() {
        return externalFields;
    }

    public void setExternalFields(Map<String, String> externalFields) {
        this.externalFields = externalFields;
    }

    public HuaweiFaceRefStatus getStatus() {
        return status;
    }

    public void setStatus(HuaweiFaceRefStatus status) {
        this.status = status;
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
            status = HuaweiFaceRefStatus.ACTIVE;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
