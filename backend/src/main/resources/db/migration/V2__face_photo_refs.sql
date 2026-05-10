CREATE TABLE face_photo (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account (id),
    obs_bucket VARCHAR(128) NOT NULL,
    obs_region VARCHAR(128) NOT NULL,
    obs_object_key VARCHAR(255) NOT NULL,
    content_type VARCHAR(64) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    storage_provider VARCHAR(64) NOT NULL,
    detect_status VARCHAR(32) NOT NULL,
    register_status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(255),
    failure_code VARCHAR(64),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL REFERENCES user_account (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_face_photo_detect_status CHECK (detect_status IN ('PENDING', 'PASSED', 'FAILED')),
    CONSTRAINT chk_face_photo_register_status CHECK (
        register_status IN ('PENDING', 'ACTIVE', 'FAILED', 'DELETE_PENDING', 'DELETE_FAILED', 'DELETED')
    )
);

CREATE INDEX idx_face_photo_user_enabled_created_at
    ON face_photo (user_id, enabled, created_at DESC);

CREATE TABLE huawei_face_ref (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES user_account (id),
    face_photo_id UUID NOT NULL REFERENCES face_photo (id),
    face_set_name VARCHAR(128) NOT NULL,
    frs_face_id VARCHAR(128) NOT NULL,
    external_image_id VARCHAR(128) NOT NULL,
    external_fields JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_huawei_face_ref_frs_face_id UNIQUE (frs_face_id),
    CONSTRAINT chk_huawei_face_ref_status CHECK (
        status IN ('ACTIVE', 'DELETE_PENDING', 'DELETE_FAILED', 'DELETED', 'ORPHANED')
    )
);

CREATE INDEX idx_huawei_face_ref_photo_status
    ON huawei_face_ref (face_photo_id, status);

CREATE INDEX idx_huawei_face_ref_user_status
    ON huawei_face_ref (user_id, status);
