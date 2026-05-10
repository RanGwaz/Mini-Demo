--liquibase formatted sql

--changeset rangwaz:066-001-create-content-rebuild-tasks
CREATE TABLE IF NOT EXISTS content_rebuild_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    scope_type VARCHAR(32) NOT NULL DEFAULT 'ALL',
    scope_id VARCHAR(128) NULL,
    batch_id BIGINT NULL,
    post_id BIGINT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    params_json JSON NULL,
    error_message VARCHAR(1024) NULL,
    operator_id BIGINT NULL,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_rebuild_tasks_status_created (status, created_at),
    INDEX idx_rebuild_tasks_type_status (task_type, status),
    INDEX idx_rebuild_tasks_scope (scope_type, scope_id),
    INDEX idx_rebuild_tasks_batch (batch_id),
    INDEX idx_rebuild_tasks_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
