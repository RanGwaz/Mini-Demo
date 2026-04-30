CREATE TABLE IF NOT EXISTS recommendation_tag_dictionary (
    tag_id INT NOT NULL,
    term VARCHAR(128) NOT NULL,
    source_count INT NOT NULL DEFAULT 0,
    post_count INT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (tag_id),
    UNIQUE KEY uk_recommendation_tag_dictionary_term (term),
    KEY idx_recommendation_tag_dictionary_post_count (post_count DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
