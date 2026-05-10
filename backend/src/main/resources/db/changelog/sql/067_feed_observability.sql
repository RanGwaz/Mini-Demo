--liquibase formatted sql

--changeset rangwaz:067-001-create-feed-request-logs
CREATE TABLE IF NOT EXISTS feed_request_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(96) NOT NULL,
    user_id BIGINT NULL,
    surface VARCHAR(64) NOT NULL,
    page_no INT NOT NULL DEFAULT 1,
    page_size INT NOT NULL DEFAULT 24,
    seed VARCHAR(128) NULL,
    filters_json JSON NULL,
    user_segment VARCHAR(64) NOT NULL DEFAULT 'anonymous',
    experiment_id VARCHAR(128) NULL,
    experiment_bucket VARCHAR(64) NULL,
    total_candidates INT NOT NULL DEFAULT 0,
    returned_count INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    degraded TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feed_request_logs_request_id (request_id),
    INDEX idx_feed_request_logs_user_created (user_id, created_at),
    INDEX idx_feed_request_logs_surface_created (surface, created_at),
    INDEX idx_feed_request_logs_experiment_created (experiment_id, experiment_bucket, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:067-002-create-feed-impression-logs
CREATE TABLE IF NOT EXISTS feed_impression_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(96) NOT NULL,
    user_id BIGINT NULL,
    post_id BIGINT NOT NULL,
    rank_position INT NOT NULL,
    recall_source VARCHAR(128) NULL,
    rank_score DECIMAL(12,4) NOT NULL DEFAULT 0,
    channel_code VARCHAR(64) NULL,
    topic_names VARCHAR(512) NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_feed_impression_logs_request (request_id, rank_position),
    INDEX idx_feed_impression_logs_user_created (user_id, created_at),
    INDEX idx_feed_impression_logs_post_created (post_id, created_at),
    INDEX idx_feed_impression_logs_source_created (recall_source, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:067-003-create-recommendation-experiments
CREATE TABLE IF NOT EXISTS recommendation_experiments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    bucket_key VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    traffic_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    config_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_reco_experiment_name_bucket (name, bucket_key),
    INDEX idx_reco_experiment_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:067-004-create-recommendation-source-snapshots
CREATE TABLE IF NOT EXISTS recommendation_source_snapshots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id VARCHAR(96) NULL,
    source_name VARCHAR(128) NOT NULL,
    candidate_count INT NOT NULL DEFAULT 0,
    selected_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    snapshot_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_reco_source_snapshot_request (request_id),
    INDEX idx_reco_source_snapshot_source_time (source_name, snapshot_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
