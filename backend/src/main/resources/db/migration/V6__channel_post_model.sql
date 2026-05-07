CREATE TABLE IF NOT EXISTS channels (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    icon VARCHAR(255),
    sort_order INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO channels (code, name, description, sort_order) VALUES
('campus', '校园生活', '大学生校园日常、吐槽、学习、生活分享', 1),
('anime_outfit', '二次元穿搭', '二次元风格穿搭、角色灵感、日系搭配', 2),
('pet', '宠物日常', '猫狗萌宠、宠物生活、治愈瞬间', 3),
('photography', '摄影分享', '摄影作品、拍摄参数、地点分享', 4),
('tech_moment', '程序员摸鱼', '程序员日常、AI工具、效率工具、技术趣事', 5)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    sort_order = VALUES(sort_order),
    enabled = 1;

ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS channel_code VARCHAR(64) NULL AFTER author_id,
    ADD COLUMN IF NOT EXISTS post_type VARCHAR(64) NULL AFTER channel_code,
    ADD COLUMN IF NOT EXISTS extra TEXT NULL AFTER content,
    ADD COLUMN IF NOT EXISTS share_count INT NOT NULL DEFAULT 0 AFTER comment_count;

UPDATE posts
SET channel_code = CASE
        WHEN topic_cluster_key IN ('campus', 'campus_life') OR topic_path LIKE '%校园%' THEN 'campus'
        WHEN topic_cluster_key = 'anime_outfit' OR topic_path LIKE '%二次元%' OR topic_path LIKE '%穿搭%' THEN 'anime_outfit'
        WHEN topic_cluster_key IN ('pet', 'pets') OR topic_path LIKE '%宠物%' THEN 'pet'
        WHEN topic_cluster_key = 'photography' OR topic_path LIKE '%摄影%' THEN 'photography'
        WHEN topic_cluster_key IN ('tech_moment', 'tool_post') OR topic_path LIKE '%程序员%' OR topic_path LIKE '%AI%' THEN 'tech_moment'
        ELSE 'general'
    END
WHERE channel_code IS NULL OR channel_code = '';

UPDATE posts
SET post_type = CASE channel_code
        WHEN 'campus' THEN 'campus_post'
        WHEN 'anime_outfit' THEN 'anime_outfit_post'
        WHEN 'pet' THEN 'pet_post'
        WHEN 'photography' THEN 'photography_post'
        WHEN 'tech_moment' THEN 'tech_moment_post'
        ELSE 'general_post'
    END
WHERE post_type IS NULL OR post_type = '';

UPDATE posts
SET extra = '{}'
WHERE extra IS NULL OR extra = '';

ALTER TABLE posts
    ADD INDEX IF NOT EXISTS idx_posts_channel_created (channel_code, created_at),
    ADD INDEX IF NOT EXISTS idx_posts_post_type (post_type);
