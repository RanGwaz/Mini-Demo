--liquibase formatted sql

--changeset rangwaz:068-001-create-training-datasets
CREATE TABLE IF NOT EXISTS training_datasets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    dataset_type VARCHAR(64) NOT NULL DEFAULT 'RANKING',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    split_strategy VARCHAR(128) NULL,
    source_window_start DATETIME NULL,
    source_window_end DATETIME NULL,
    row_count BIGINT NOT NULL DEFAULT 0,
    positive_count BIGINT NOT NULL DEFAULT 0,
    negative_count BIGINT NOT NULL DEFAULT 0,
    file_path VARCHAR(512) NULL,
    metrics_json JSON NULL,
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_training_datasets_type_status (dataset_type, status),
    INDEX idx_training_datasets_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:068-002-create-model-versions
CREATE TABLE IF NOT EXISTS model_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name VARCHAR(160) NOT NULL,
    version VARCHAR(96) NOT NULL,
    model_type VARCHAR(64) NOT NULL DEFAULT 'RANKING',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    dataset_id BIGINT NULL,
    artifact_uri VARCHAR(512) NULL,
    shadow_enabled TINYINT(1) NOT NULL DEFAULT 0,
    online_enabled TINYINT(1) NOT NULL DEFAULT 0,
    traffic_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    guardrail_json JSON NULL,
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_versions_name_version (model_name, version),
    INDEX idx_model_versions_type_status (model_type, status),
    INDEX idx_model_versions_dataset (dataset_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:068-003-create-offline-eval-reports
CREATE TABLE IF NOT EXISTS offline_eval_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_version_id BIGINT NULL,
    dataset_id BIGINT NULL,
    auc DECIMAL(10,6) NULL,
    ndcg DECIMAL(10,6) NULL,
    recall_score DECIMAL(10,6) NULL,
    precision_score DECIMAL(10,6) NULL,
    metrics_json JSON NULL,
    report_path VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'READY',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_eval_reports_model (model_version_id),
    INDEX idx_eval_reports_dataset (dataset_id),
    INDEX idx_eval_reports_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:068-004-create-content-moderation-cases
CREATE TABLE IF NOT EXISTS content_moderation_cases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    reporter_id BIGINT NULL,
    reason VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    assigned_to BIGINT NULL,
    decision VARCHAR(64) NULL,
    action_note VARCHAR(1024) NULL,
    operator_id BIGINT NULL,
    resolved_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_moderation_cases_status_priority (status, priority),
    INDEX idx_moderation_cases_post (post_id),
    INDEX idx_moderation_cases_reporter (reporter_id),
    INDEX idx_moderation_cases_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:068-005-create-account-moderation-actions
CREATE TABLE IF NOT EXISTS account_moderation_actions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    operator_id BIGINT NULL,
    action_type VARCHAR(64) NOT NULL,
    reason VARCHAR(512) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_account_actions_user_status (user_id, status),
    INDEX idx_account_actions_type_created (action_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:068-006-create-creator-profiles
CREATE TABLE IF NOT EXISTS creator_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    domain_tags VARCHAR(512) NULL,
    creator_level VARCHAR(64) NOT NULL DEFAULT 'SEED',
    quality_score DECIMAL(10,4) NOT NULL DEFAULT 0,
    violation_status VARCHAR(64) NOT NULL DEFAULT 'NORMAL',
    monetization_status VARCHAR(64) NOT NULL DEFAULT 'DISABLED',
    commercial_status VARCHAR(64) NOT NULL DEFAULT 'NORMAL',
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_creator_profiles_user (user_id),
    INDEX idx_creator_profiles_level_quality (creator_level, quality_score),
    INDEX idx_creator_profiles_monetization (monetization_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--changeset rangwaz:068-007-create-commercial-content-profiles
CREATE TABLE IF NOT EXISTS commercial_content_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id BIGINT NOT NULL,
    brand_name VARCHAR(160) NULL,
    disclosure_type VARCHAR(64) NOT NULL DEFAULT 'NONE',
    campaign_code VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    bid_type VARCHAR(64) NULL,
    budget_cents BIGINT NOT NULL DEFAULT 0,
    landing_url VARCHAR(512) NULL,
    config_json JSON NULL,
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_commercial_content_post (post_id),
    INDEX idx_commercial_content_status (status),
    INDEX idx_commercial_content_campaign (campaign_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
