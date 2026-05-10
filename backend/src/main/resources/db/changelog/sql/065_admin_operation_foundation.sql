--liquibase formatted sql

--changeset rangwaz:065-001-create-content-import-batches
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_import_batches'
CREATE TABLE content_import_batches (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    source_type VARCHAR(64) NOT NULL DEFAULT 'EDITORIAL',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    operator_id BIGINT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_content_import_batches_status_created (status, created_at),
    KEY idx_content_import_batches_operator (operator_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:065-002-create-content-import-items
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_import_items'
CREATE TABLE content_import_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    post_id BIGINT NULL,
    title VARCHAR(128) NULL,
    content TEXT NULL,
    channel_code VARCHAR(64) NULL,
    topic_names TEXT NULL,
    image_urls TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    error_message VARCHAR(512) NULL,
    raw_payload TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_content_import_items_batch_status (batch_id, status),
    KEY idx_content_import_items_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:065-003-create-admin-operation-logs
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_operation_logs'
CREATE TABLE admin_operation_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operator_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NULL,
    detail_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_operation_logs_operator_created (operator_id, created_at),
    KEY idx_admin_operation_logs_target_created (target_type, target_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:065-004-bootstrap-first-admin
UPDATE users
SET roles = CASE
    WHEN roles IS NULL OR roles = '' THEN 'ROLE_ADMIN,ROLE_USER'
    WHEN roles NOT LIKE '%ROLE_ADMIN%' THEN CONCAT('ROLE_ADMIN,', roles)
    ELSE roles
END
WHERE id = (
    SELECT seed.id FROM (
        SELECT MIN(id) AS id FROM users
    ) seed
)
AND (roles IS NULL OR roles NOT LIKE '%ROLE_ADMIN%');
