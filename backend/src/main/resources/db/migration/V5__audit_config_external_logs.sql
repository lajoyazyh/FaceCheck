CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    action VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id UUID,
    summary VARCHAR(255) NOT NULL,
    detail_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_actor_created_at
    ON audit_log (actor_user_id, created_at DESC);

CREATE INDEX idx_audit_log_action_created_at
    ON audit_log (action, created_at DESC);

CREATE TABLE system_config (
    id UUID PRIMARY KEY,
    config_key VARCHAR(128) NOT NULL,
    config_value TEXT NOT NULL,
    value_type VARCHAR(16) NOT NULL,
    description VARCHAR(255),
    updated_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_config_key UNIQUE (config_key),
    CONSTRAINT chk_system_config_value_type CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON'))
);

CREATE INDEX idx_system_config_updated_at
    ON system_config (updated_at DESC);

INSERT INTO system_config (id, config_key, config_value, value_type, description, updated_by)
VALUES
    ('50000000-0000-0000-0000-000000000001', 'frs.face-set-name', 'facecheck-default', 'STRING',
     'Configured Huawei face set name', '00000000-0000-0000-0000-000000000000'),
    ('50000000-0000-0000-0000-000000000002', 'frs.similarity-threshold', '85', 'NUMBER',
     'Minimum compare threshold for a successful face match', '00000000-0000-0000-0000-000000000000'),
    ('50000000-0000-0000-0000-000000000003', 'frs.liveness-enabled', 'false', 'BOOLEAN',
     'Reserved liveness switch for later cloud integration', '00000000-0000-0000-0000-000000000000'),
    ('50000000-0000-0000-0000-000000000004', 'checkin.idempotency-ttl-hours', '24', 'NUMBER',
     'Redis idempotency cache TTL in hours', '00000000-0000-0000-0000-000000000000'),
    ('50000000-0000-0000-0000-000000000005', 'checkin.rate-limit-window-seconds', '60', 'NUMBER',
     'Anonymous check-in rate-limit time window', '00000000-0000-0000-0000-000000000000'),
    ('50000000-0000-0000-0000-000000000006', 'checkin.rate-limit-max-requests', '5', 'NUMBER',
     'Maximum anonymous check-in requests per rate-limit window', '00000000-0000-0000-0000-000000000000'),
    ('50000000-0000-0000-0000-000000000007', 'image.max-file-size-bytes', '10485760', 'NUMBER',
     'Maximum accepted face/check-in image file size in bytes', '00000000-0000-0000-0000-000000000000');

CREATE TABLE external_service_call_log (
    id UUID PRIMARY KEY,
    service_name VARCHAR(32) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    request_id VARCHAR(128),
    related_entity_type VARCHAR(64),
    related_entity_id UUID,
    status VARCHAR(32) NOT NULL,
    latency_ms INTEGER,
    error_code VARCHAR(64),
    error_summary VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_external_service_name CHECK (service_name IN ('HUAWEI_FRS', 'HUAWEI_OBS')),
    CONSTRAINT chk_external_service_status CHECK (status IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'RATE_LIMITED', 'RETRYING'))
);

CREATE INDEX idx_external_service_call_service_created_at
    ON external_service_call_log (service_name, created_at DESC);

CREATE INDEX idx_external_service_call_related_entity
    ON external_service_call_log (related_entity_type, related_entity_id, created_at DESC);

ALTER TABLE attendance_checkin_attempt
    ADD COLUMN review_note TEXT,
    ADD COLUMN reviewed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reviewed_by_user_id UUID,
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_attendance_checkin_attempt_retry_count CHECK (retry_count >= 0);

CREATE INDEX idx_attendance_checkin_attempt_reviewed_created_at
    ON attendance_checkin_attempt (reviewed, created_at DESC);
