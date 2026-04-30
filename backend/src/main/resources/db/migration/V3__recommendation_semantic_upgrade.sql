ALTER TABLE posts
    ADD COLUMN topic_path VARCHAR(255) NULL AFTER tags,
    ADD COLUMN semantic_tags VARCHAR(1024) NULL AFTER topic_path,
    ADD COLUMN style_tags VARCHAR(512) NULL AFTER semantic_tags,
    ADD COLUMN taxonomy_json TEXT NULL AFTER style_tags,
    ADD COLUMN topic_cluster_key VARCHAR(128) NULL AFTER taxonomy_json,
    ADD COLUMN subtopic_cluster_key VARCHAR(128) NULL AFTER topic_cluster_key,
    ADD COLUMN quality_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000 AFTER hot_score,
    ADD COLUMN aesthetic_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000 AFTER quality_score,
    ADD COLUMN safety_score DECIMAL(10,4) NOT NULL DEFAULT 1.0000 AFTER aesthetic_score,
    ADD COLUMN embedding_version VARCHAR(64) NULL AFTER safety_score,
    ADD COLUMN taxonomy_version VARCHAR(64) NULL AFTER embedding_version;

ALTER TABLE user_events
    ADD COLUMN request_id VARCHAR(64) NULL AFTER target_id,
    ADD COLUMN session_id VARCHAR(64) NULL AFTER request_id,
    ADD COLUMN surface VARCHAR(64) NULL AFTER session_id,
    ADD COLUMN page_no INT NULL AFTER surface,
    ADD COLUMN rank_position INT NULL AFTER page_no,
    ADD COLUMN recall_source VARCHAR(255) NULL AFTER rank_position,
    ADD COLUMN dwell_ms BIGINT NULL AFTER recall_source,
    ADD COLUMN device_type VARCHAR(32) NULL AFTER dwell_ms,
    ADD COLUMN experiment_id VARCHAR(64) NULL AFTER device_type;

ALTER TABLE user_features
    ADD COLUMN top_interest_topics VARCHAR(512) NULL AFTER top_interest_tags,
    ADD COLUMN preferred_styles VARCHAR(512) NULL AFTER top_interest_topics;

ALTER TABLE post_features
    ADD COLUMN topic_path VARCHAR(255) NULL AFTER tags,
    ADD COLUMN semantic_tags VARCHAR(1024) NULL AFTER topic_path,
    ADD COLUMN style_tags VARCHAR(512) NULL AFTER semantic_tags,
    ADD COLUMN quality_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000 AFTER author_avg_hot_score,
    ADD COLUMN aesthetic_score DECIMAL(10,4) NOT NULL DEFAULT 0.0000 AFTER quality_score,
    ADD COLUMN safety_score DECIMAL(10,4) NOT NULL DEFAULT 1.0000 AFTER aesthetic_score;

CREATE TABLE IF NOT EXISTS topic_clusters (
    cluster_key VARCHAR(128) PRIMARY KEY,
    parent_cluster_key VARCHAR(128) NULL,
    cluster_level TINYINT NOT NULL,
    cluster_label VARCHAR(255) NOT NULL,
    keywords_json TEXT NULL,
    sample_post_ids_json TEXT NULL,
    post_count INT NOT NULL DEFAULT 0,
    taxonomy_version VARCHAR(64) NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX idx_posts_topic_path ON posts(topic_path);
CREATE INDEX idx_posts_topic_cluster_key ON posts(topic_cluster_key);
CREATE INDEX idx_posts_subtopic_cluster_key ON posts(subtopic_cluster_key);
CREATE INDEX idx_posts_quality_aesthetic_created ON posts(quality_score, aesthetic_score, created_at);
CREATE INDEX idx_user_events_request_id ON user_events(request_id);
CREATE INDEX idx_user_events_session_id ON user_events(session_id);
CREATE INDEX idx_user_events_surface_created ON user_events(surface, created_at);
