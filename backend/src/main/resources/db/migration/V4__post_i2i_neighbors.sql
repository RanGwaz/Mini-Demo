CREATE TABLE IF NOT EXISTS post_i2i_neighbors (
    post_id BIGINT NOT NULL,
    neighbor_post_id BIGINT NOT NULL,
    score DOUBLE NOT NULL DEFAULT 0,
    co_view_count BIGINT NOT NULL DEFAULT 0,
    co_click_count BIGINT NOT NULL DEFAULT 0,
    co_detail_count BIGINT NOT NULL DEFAULT 0,
    co_like_count BIGINT NOT NULL DEFAULT 0,
    co_favorite_count BIGINT NOT NULL DEFAULT 0,
    co_comment_count BIGINT NOT NULL DEFAULT 0,
    co_share_count BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, neighbor_post_id)
);

CREATE INDEX idx_post_i2i_score ON post_i2i_neighbors(post_id, score);
CREATE INDEX idx_post_i2i_neighbor ON post_i2i_neighbors(neighbor_post_id);
