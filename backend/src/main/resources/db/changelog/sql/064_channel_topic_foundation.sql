--liquibase formatted sql

--changeset rangwaz:064-001-channels-icon-url
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'icon_url'
ALTER TABLE channels
    ADD COLUMN icon_url VARCHAR(255) NULL AFTER icon;

--changeset rangwaz:064-002-channels-cover-url
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'cover_url'
ALTER TABLE channels
    ADD COLUMN cover_url VARCHAR(255) NULL AFTER icon_url;

--changeset rangwaz:064-003-channels-status
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'status'
ALTER TABLE channels
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' AFTER cover_url;

--changeset rangwaz:064-004-channels-nav-group
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'nav_group'
ALTER TABLE channels
    ADD COLUMN nav_group VARCHAR(32) NOT NULL DEFAULT 'MAIN' AFTER status;

--changeset rangwaz:064-005-channels-default-post-type
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'default_post_type'
ALTER TABLE channels
    ADD COLUMN default_post_type VARCHAR(64) NOT NULL DEFAULT 'general_post' AFTER nav_group;

--changeset rangwaz:064-006-channels-waterfall-enabled
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'waterfall_enabled'
ALTER TABLE channels
    ADD COLUMN waterfall_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER default_post_type;

--changeset rangwaz:064-007-channels-publish-enabled
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'publish_enabled'
ALTER TABLE channels
    ADD COLUMN publish_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER waterfall_enabled;

--changeset rangwaz:064-008-channels-recommend-enabled
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'recommend_enabled'
ALTER TABLE channels
    ADD COLUMN recommend_enabled TINYINT(1) NOT NULL DEFAULT 1 AFTER publish_enabled;

--changeset rangwaz:064-009-channels-config-json
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND COLUMN_NAME = 'config_json'
ALTER TABLE channels
    ADD COLUMN config_json TEXT NULL AFTER recommend_enabled;

--changeset rangwaz:064-010-channels-status-index
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channels' AND INDEX_NAME = 'idx_channels_status_sort'
CREATE INDEX idx_channels_status_sort ON channels(status, sort_order, code);

--changeset rangwaz:064-011-create-topics
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topics'
CREATE TABLE topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    description VARCHAR(255) NULL,
    cover_url VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    risk_level VARCHAR(32) NOT NULL DEFAULT 'NORMAL',
    topic_type VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    source VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    parent_topic_id BIGINT NULL,
    post_count INT NOT NULL DEFAULT 0,
    follower_count INT NOT NULL DEFAULT 0,
    hot_score DECIMAL(16,4) NOT NULL DEFAULT 0,
    last_trended_at DATETIME NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_topics_slug (slug),
    KEY idx_topics_status_hot (status, hot_score, updated_at),
    KEY idx_topics_name (name),
    KEY idx_topics_parent (parent_topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:064-012-create-topic-aliases
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topic_aliases'
CREATE TABLE topic_aliases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    alias VARCHAR(64) NOT NULL,
    normalized_alias VARCHAR(128) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'SYSTEM',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_aliases_normalized_alias (normalized_alias),
    KEY idx_topic_aliases_topic_id (topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:064-013-create-post-topics
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_topics'
CREATE TABLE post_topics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'USER',
    confidence DECIMAL(8,4) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_topics_post_topic (post_id, topic_id),
    KEY idx_post_topics_topic_post (topic_id, post_id),
    KEY idx_post_topics_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:064-014-create-user-topic-follows
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_topic_follows'
CREATE TABLE user_topic_follows (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    source VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_topic_follows_user_topic (user_id, topic_id),
    KEY idx_user_topic_follows_topic_status (topic_id, status),
    KEY idx_user_topic_follows_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:064-015-create-topic-channel-bindings
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topic_channel_bindings'
CREATE TABLE topic_channel_bindings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    channel_code VARCHAR(64) NOT NULL,
    weight DECIMAL(8,4) NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_channel_bindings_topic_channel (topic_id, channel_code),
    KEY idx_topic_channel_bindings_channel_status (channel_code, status, weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:064-016-create-topic-trend-snapshots
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topic_trend_snapshots'
CREATE TABLE topic_trend_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    topic_id BIGINT NOT NULL,
    window_type VARCHAR(32) NOT NULL,
    post_count INT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    interaction_count BIGINT NOT NULL DEFAULT 0,
    hot_score DECIMAL(16,4) NOT NULL DEFAULT 0,
    snapshot_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_trend_snapshots_topic_window_time (topic_id, window_type, snapshot_at),
    KEY idx_topic_trend_snapshots_window_hot (window_type, hot_score, snapshot_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:064-017-create-topic-merge-logs
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topic_merge_logs'
CREATE TABLE topic_merge_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    from_topic_id BIGINT NOT NULL,
    to_topic_id BIGINT NOT NULL,
    operator_id BIGINT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_topic_merge_logs_from_topic (from_topic_id),
    KEY idx_topic_merge_logs_to_topic (to_topic_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--changeset rangwaz:064-018-seed-enterprise-channels
INSERT INTO channels (
    code,
    name,
    description,
    icon,
    icon_url,
    cover_url,
    sort_order,
    enabled,
    status,
    nav_group,
    default_post_type,
    waterfall_enabled,
    publish_enabled,
    recommend_enabled,
    config_json
) VALUES
('campus', '校园生活', '宿舍、课程、自习、社团、食堂、校园情绪与日常记录', '', '', '', 1, 1, 'ACTIVE', 'MAIN', 'campus_post', 1, 1, 1, '{"accent":"campus","layout":"waterfall"}'),
('photography', '摄影', '街拍、人像、风景、胶片、后期、设备和拍摄故事', '', '', '', 2, 1, 'ACTIVE', 'MAIN', 'photography_post', 1, 1, 1, '{"accent":"photography","layout":"waterfall"}'),
('pet', '宠物日常', '猫狗、养宠经验、领养、洗护和治愈瞬间', '', '', '', 3, 1, 'ACTIVE', 'MAIN', 'pet_post', 1, 1, 1, '{"accent":"pet","layout":"waterfall"}'),
('anime_outfit', '二次元穿搭', '漫展、COS、谷子、痛包和二次元风格穿搭', '', '', '', 4, 1, 'ACTIVE', 'MAIN', 'anime_outfit_post', 1, 1, 1, '{"accent":"anime","layout":"waterfall"}'),
('overseas_life', '留学生活', '申请、租房、校园、城市、打工和文化差异', '', '', '', 5, 1, 'ACTIVE', 'MAIN', 'overseas_life_post', 1, 1, 1, '{"accent":"overseas","layout":"waterfall"}'),
('ai_tools', 'AI/效率工具', 'AI 工具、开发工具、效率方法和工作流分享', '', '', '', 6, 1, 'ACTIVE', 'MAIN', 'ai_tool_post', 0, 1, 1, '{"accent":"ai_tools","layout":"dense"}'),
('food_explore', '美食探店', '城市美食、菜系、菜单、性价比和真实避雷', '', '', '', 7, 1, 'ACTIVE', 'MAIN', 'food_post', 1, 1, 1, '{"accent":"food","layout":"waterfall"}'),
('weekend_trip', '旅行周末', '短途旅行、城市漫游、攻略、路线和周末计划', '', '', '', 8, 1, 'ACTIVE', 'MAIN', 'travel_post', 1, 1, 1, '{"accent":"travel","layout":"waterfall"}'),
('home_life', '家居生活', '租房改造、桌搭、收纳、好物和生活方式', '', '', '', 9, 1, 'ACTIVE', 'MAIN', 'home_life_post', 1, 1, 1, '{"accent":"home","layout":"waterfall"}')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    icon_url = VALUES(icon_url),
    cover_url = VALUES(cover_url),
    sort_order = VALUES(sort_order),
    enabled = VALUES(enabled),
    status = VALUES(status),
    nav_group = VALUES(nav_group),
    default_post_type = VALUES(default_post_type),
    waterfall_enabled = VALUES(waterfall_enabled),
    publish_enabled = VALUES(publish_enabled),
    recommend_enabled = VALUES(recommend_enabled),
    config_json = VALUES(config_json);

--changeset rangwaz:064-019-deprecate-old-tech-moment-channel
UPDATE channels
SET status = 'INACTIVE',
    enabled = 0,
    publish_enabled = 0,
    recommend_enabled = 0,
    config_json = '{"deprecatedBy":"ai_tools"}'
WHERE code = 'tech_moment';

--changeset rangwaz:064-020-seed-topics
INSERT INTO topics (name, slug, description, status, risk_level, topic_type, source, hot_score) VALUES
('宿舍日常', 'campus-dorm-life', '宿舍空间、室友关系和校园生活记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('期末周', 'campus-finals-week', '期末复习、考试安排和学习节奏', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('自习室', 'campus-study-room', '自习室、图书馆和学习效率', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('社团活动', 'campus-clubs', '校园社团、活动和兴趣组织', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('食堂测评', 'campus-canteen-review', '食堂窗口、菜品测评和校园餐饮', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('课程笔记', 'campus-course-notes', '课程记录、笔记和学习资料整理', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('校园吐槽', 'campus-rant', '校园生活里的真实吐槽和轻松分享', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('早八生存', 'campus-morning-class', '早八课程、通勤和精神状态', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('校园穿搭', 'campus-outfit', '适合上课、社团和校园日常的穿搭', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('操场夜跑', 'campus-night-run', '校园运动、夜跑和健康生活', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('考研记录', 'campus-grad-exam', '考研备考、资料和复盘', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('课堂瞬间', 'campus-classroom-moment', '课堂、实验课和小组作业瞬间', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('校园摄影', 'campus-photography', '校园里的光影、建筑和日常照片', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('奖学金经验', 'campus-scholarship', '奖学金申请、绩点和材料经验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宿舍改造', 'campus-dorm-makeover', '宿舍桌面、收纳和空间改造', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('图书馆日常', 'campus-library-life', '图书馆学习、座位和阅读记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('校园猫咪', 'campus-cats', '校园里的猫咪和治愈日常', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('毕业季', 'campus-graduation', '毕业照、论文、离校和告别', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('交换生活', 'campus-exchange-life', '交换项目、课程和校园体验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('大学新生', 'campus-freshman', '入学准备、新生适应和校园指南', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),

('街拍', 'photo-street', '城市街头、人文和瞬间捕捉', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('人像摄影', 'photo-portrait', '人像拍摄、构图和后期', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('胶片感', 'photo-film-look', '胶片色彩、颗粒和复古影调', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('风景摄影', 'photo-landscape', '自然风景、城市风景和旅行照片', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('夜景摄影', 'photo-nightscape', '夜景、长曝光和城市灯光', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('相机设备', 'photo-camera-gear', '相机、镜头和摄影配件', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('修图后期', 'photo-editing', '后期调色、修图流程和软件技巧', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('构图练习', 'photo-composition', '构图、视角和画面训练', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('光影记录', 'photo-light-shadow', '光线、阴影和氛围捕捉', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('旅行摄影', 'photo-travel', '旅途中的摄影记录和地点分享', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('城市漫步', 'photo-citywalk', '城市观察、街区和日常影像', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('手机摄影', 'photo-mobile', '手机拍摄、修图和构图技巧', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('黑白摄影', 'photo-black-white', '黑白影像、明暗和情绪表达', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('拍照姿势', 'photo-pose', '人像姿势、动作和拍摄沟通', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('摄影地点', 'photo-location', '适合拍照的地点、路线和时段', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('扫街日记', 'photo-street-diary', '扫街路线、观察和作品记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('镜头选择', 'photo-lens-choice', '不同镜头的视角和适用场景', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('RAW 后期', 'photo-raw-editing', 'RAW 文件、调色和细节恢复', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('拍摄参数', 'photo-exif-settings', '光圈、快门、ISO 和参数经验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('摄影灵感', 'photo-inspiration', '主题、作品和拍摄灵感收集', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),

('猫咪日常', 'pet-cat-life', '猫咪生活、行为和治愈瞬间', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('狗狗日常', 'pet-dog-life', '狗狗生活、训练和陪伴记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('养宠经验', 'pet-care-tips', '喂养、健康、清洁和养宠经验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('萌宠瞬间', 'pet-cute-moment', '宠物可爱、搞笑和治愈片段', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('领养故事', 'pet-adoption-story', '领养、救助和新家适应', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宠物洗护', 'pet-grooming', '洗澡、梳毛、护理和用品', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宠物健康', 'pet-health', '驱虫、体检、饮食和健康观察', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('新手养猫', 'pet-new-cat-owner', '新手养猫准备和避坑', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('新手养狗', 'pet-new-dog-owner', '新手养狗训练和生活准备', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宠物用品', 'pet-products', '猫狗用品、玩具和好物测评', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('多宠家庭', 'pet-multi-pet-home', '多只宠物共处和家庭日常', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宠物摄影', 'pet-photography', '给宠物拍照的布光、构图和技巧', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('猫饭狗饭', 'pet-meals', '宠物饮食、配餐和零食记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('毛孩子档案', 'pet-profile', '宠物成长记录和资料卡', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宠物出行', 'pet-travel', '带宠出行、寄养和公共空间经验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宠物训练', 'pet-training', '基础训练、习惯养成和互动游戏', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('治愈陪伴', 'pet-healing-company', '宠物陪伴、情绪和生活治愈', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('猫砂测评', 'pet-litter-review', '猫砂、除臭和清洁体验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宠物避坑', 'pet-pitfall-notes', '养宠踩坑、误区和真实提醒', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宠物成长', 'pet-growth', '从幼宠到成年期的成长记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),

('漫展记录', 'anime-expo-diary', '漫展现场、摊位和角色记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('COS 穿搭', 'anime-cos-outfit', 'COS 服装、妆造和拍摄分享', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('痛包搭配', 'anime-ita-bag', '痛包设计、谷子展示和搭配灵感', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('谷子分享', 'anime-merch-share', '徽章、立牌、卡片和周边收藏', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('日系校园', 'anime-jp-campus-style', '日系校园感穿搭和造型', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('Lolita 日常', 'anime-lolita-daily', 'Lolita 服饰、配饰和日常搭配', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('JK 制服', 'anime-jk-uniform', 'JK 制服、配色和拍摄记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('角色灵感', 'anime-character-inspiration', '从角色设定提炼穿搭灵感', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('妆造记录', 'anime-makeup-styling', '妆容、发型、配饰和造型复盘', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('手办展示', 'anime-figure-display', '手办、模型和桌面展示', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('二次元房间', 'anime-room-setup', '房间布置、桌搭和收藏展示', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('游戏美术', 'anime-game-art', '游戏角色、美术风格和灵感收藏', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('拍摄布景', 'anime-shooting-scene', '二次元主题拍摄布景和道具', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('同人活动', 'anime-fan-event', '同人展、摊位和活动记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('发色灵感', 'anime-hair-color', '发色、假发和角色造型', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('配饰清单', 'anime-accessory-list', '配饰、包、鞋和小物清单', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宅家穿搭', 'anime-home-outfit', '居家、出门和轻二次元穿搭', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('番剧灵感', 'anime-series-inspiration', '番剧角色、色彩和氛围灵感', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('拍照动作', 'anime-photo-pose', '适合二次元穿搭的拍照动作', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('穿搭复盘', 'anime-outfit-review', '单品、色彩、妆造和拍摄复盘', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),

('留学申请', 'overseas-application', '申请材料、时间线和经验复盘', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('海外租房', 'overseas-renting', '租房平台、看房、合同和避坑', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('海外校园', 'overseas-campus', '校园设施、课程和学习生活', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('城市生活', 'overseas-city-life', '海外城市日常、交通和生活便利', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('留学生打工', 'overseas-part-time-job', '兼职、实习和工作经验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('文化差异', 'overseas-culture-difference', '跨文化沟通和生活观察', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('行李清单', 'overseas-packing-list', '出国行李、必备物品和取舍', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('签证经验', 'overseas-visa', '签证材料、面签和续签经验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('语言学习', 'overseas-language-learning', '语言考试、口语和学习方法', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('海外做饭', 'overseas-cooking', '留学生做饭、采购和厨房日常', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('课程选择', 'overseas-course-selection', '选课、教授、作业和评分经验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('海外旅行', 'overseas-travel', '留学期间旅行路线和预算', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('实习求职', 'overseas-career', '简历、面试、实习和求职规划', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('省钱生活', 'overseas-saving-money', '预算、折扣、二手和省钱技巧', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('海外社交', 'overseas-social-life', '同学、社团、朋友和社交适应', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('宿舍公寓', 'overseas-dorm-apartment', '宿舍、公寓和合租生活', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('开学准备', 'overseas-orientation', '报到、选课、银行卡和电话卡', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('安全避坑', 'overseas-safety', '治安、诈骗、交通和生活安全', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('回国准备', 'overseas-return-home', '毕业、行李、证明和回国规划', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('留学情绪', 'overseas-emotion', '孤独、压力和自我调节', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),

('AI 工具', 'ai-tools', 'AI 产品、模型工具和使用体验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('效率工作流', 'ai-productivity-workflow', '自动化、知识管理和效率流程', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('开发工具', 'ai-dev-tools', '代码编辑器、调试和工程效率工具', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('提示词', 'ai-prompts', '提示词写法、模板和调参经验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('自动化', 'ai-automation', '自动化脚本、工作流和工具组合', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('知识管理', 'ai-knowledge-management', '笔记、检索、知识库和个人系统', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('AI 绘图', 'ai-image-generation', '文生图、图生图和视觉创作流程', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('AI 写作', 'ai-writing', '写作辅助、改写和内容生成', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('模型评测', 'ai-model-review', '模型能力、速度、价格和效果评测', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('插件推荐', 'ai-plugin-recommendation', '浏览器、编辑器和平台插件分享', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('办公效率', 'ai-office-productivity', '表格、文档、会议和办公场景工具', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('代码重构', 'ai-code-refactor', '代码理解、重构和工程实践', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('低代码', 'ai-low-code', '低代码平台、自动建站和应用搭建', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('数据分析', 'ai-data-analysis', '数据清洗、分析和可视化工具', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('AI 学习', 'ai-learning', 'AI 基础、课程和学习路径', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('工具对比', 'ai-tool-comparison', '同类工具价格、体验和效果对比', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('免费工具', 'ai-free-tools', '免费额度、开源工具和替代方案', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('工作台搭建', 'ai-workbench-setup', '个人工作台、插件和配置清单', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('AI 新闻', 'ai-news', 'AI 产品更新、行业消息和趋势', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('工具避坑', 'ai-tool-pitfalls', '工具限制、坑点和使用提醒', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),

('城市美食', 'food-city-food', '城市里的餐厅、小吃和美食地图', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('探店记录', 'food-restaurant-visit', '真实探店、环境、口味和服务', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('咖啡馆', 'food-cafe', '咖啡馆、甜点和适合停留的空间', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('平价美食', 'food-budget', '学生党和上班族友好的平价美食', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('夜宵', 'food-late-night', '深夜食堂、烧烤、小吃和夜宵推荐', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('甜品', 'food-dessert', '蛋糕、面包、冰品和甜品测评', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('菜单推荐', 'food-menu-picks', '必点菜、隐藏菜单和点单攻略', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('避雷餐厅', 'food-avoid-list', '真实避雷、踩坑和提醒', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('早餐地图', 'food-breakfast-map', '早餐店、便利餐和早起美食', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('火锅烧烤', 'food-hotpot-bbq', '火锅、烧烤和聚餐选择', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('独居做饭', 'food-solo-cooking', '一人食、备餐和厨房日常', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('外卖测评', 'food-delivery-review', '外卖店铺、套餐和复购推荐', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('城市小吃', 'food-street-snack', '街边小吃、老店和地方味道', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('约会餐厅', 'food-date-restaurant', '适合约会、生日和纪念日的餐厅', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('聚餐选择', 'food-group-dining', '朋友聚会、团建和多人餐厅', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('饮品测评', 'food-drink-review', '奶茶、咖啡、果茶和饮品体验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('地方菜', 'food-local-cuisine', '地方菜系、家乡味和特色餐厅', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('美食摄影', 'food-photography', '食物拍照、布景和修图', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('省钱吃法', 'food-save-money', '团购、套餐、折扣和性价比', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('新店开业', 'food-new-restaurant', '新店、试营业和首发体验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),

('周末短途', 'trip-weekend-short', '周末两天一夜和短途路线', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('城市漫游', 'trip-citywalk', '城市步行、街区和路线记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('旅行攻略', 'trip-guide', '交通、住宿、路线和预算攻略', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('拍照路线', 'trip-photo-route', '适合拍照的旅行路线和点位', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('小众目的地', 'trip-hidden-place', '不拥挤的小众旅行目的地', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('自然徒步', 'trip-hiking', '徒步、山野、自然和户外准备', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('海边旅行', 'trip-seaside', '海边城市、沙滩和日落路线', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('古镇散步', 'trip-old-town', '古镇、老街和慢旅行记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('省钱旅行', 'trip-budget', '预算、交通、住宿和省钱技巧', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('亲子出游', 'trip-family', '适合家庭和亲子出行的地点', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('独自旅行', 'trip-solo', '一个人旅行、安全和路线规划', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('旅行穿搭', 'trip-outfit', '旅行穿搭、轻便装备和拍照效果', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('民宿体验', 'trip-homestay', '民宿、酒店和住宿体验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('自驾路线', 'trip-roadtrip', '自驾、停车、路线和沿途风景', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('展览打卡', 'trip-exhibition', '展览、美术馆和城市文化活动', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('公园野餐', 'trip-picnic', '公园、野餐和轻户外周末', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('日落地点', 'trip-sunset-place', '看日落、夜景和城市观景点', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('旅行避坑', 'trip-pitfalls', '旅行踩坑、排队、花费和真实提醒', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('本地周末', 'trip-local-weekend', '本地生活、近郊和周末计划', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('路线复盘', 'trip-route-review', '旅行结束后的路线和体验复盘', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),

('租房改造', 'home-rental-makeover', '出租屋改造、软装和预算控制', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('桌搭', 'home-desk-setup', '桌面布置、设备和效率空间', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('收纳整理', 'home-storage', '衣柜、厨房、桌面和生活收纳', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('家居好物', 'home-products', '实用家居用品和真实体验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('独居生活', 'home-solo-life', '一个人的生活节奏、做饭和空间', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('厨房日常', 'home-kitchen', '厨房收纳、餐具和做饭动线', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('卧室布置', 'home-bedroom', '卧室软装、灯光和舒适感', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('客厅灵感', 'home-living-room', '客厅布局、沙发、投影和氛围', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('清洁技巧', 'home-cleaning', '清洁工具、流程和家务效率', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('香氛氛围', 'home-fragrance', '香薰、蜡烛、灯光和居家氛围', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('小户型', 'home-small-space', '小户型空间利用和动线设计', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('阳台改造', 'home-balcony', '阳台花草、休闲区和收纳', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('智能家居', 'home-smart-devices', '智能设备、自动化和场景设置', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('家居避坑', 'home-pitfalls', '家居购买、改造和使用踩坑', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('软装配色', 'home-color-match', '软装、配色和风格统一', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('书架展示', 'home-bookshelf', '书架、收藏和展示空间', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('床品测评', 'home-bedding-review', '床品、枕头和睡眠体验', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('搬家准备', 'home-moving', '搬家清单、打包和新家整理', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('生活仪式感', 'home-ritual', '居家仪式感、慢生活和日常记录', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80),
('预算装修', 'home-budget-renovation', '低预算装修和改造方案', 'ACTIVE', 'NORMAL', 'GENERAL', 'SYSTEM', 80)
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    status = VALUES(status),
    risk_level = VALUES(risk_level),
    topic_type = VALUES(topic_type),
    source = VALUES(source),
    hot_score = VALUES(hot_score);

--changeset rangwaz:064-021-seed-topic-channel-bindings
INSERT INTO topic_channel_bindings (topic_id, channel_code, weight, status)
SELECT t.id, seed.channel_code, seed.weight, 'ACTIVE'
FROM (
    SELECT 'campus' AS channel_code, 'campus-dorm-life' AS slug, 1.0000 AS weight UNION ALL
    SELECT 'campus', 'campus-finals-week', 1.0000 UNION ALL
    SELECT 'campus', 'campus-study-room', 1.0000 UNION ALL
    SELECT 'campus', 'campus-clubs', 1.0000 UNION ALL
    SELECT 'campus', 'campus-canteen-review', 1.0000 UNION ALL
    SELECT 'campus', 'campus-course-notes', 1.0000 UNION ALL
    SELECT 'campus', 'campus-rant', 1.0000 UNION ALL
    SELECT 'campus', 'campus-morning-class', 1.0000 UNION ALL
    SELECT 'campus', 'campus-outfit', 1.0000 UNION ALL
    SELECT 'campus', 'campus-night-run', 1.0000 UNION ALL
    SELECT 'campus', 'campus-grad-exam', 1.0000 UNION ALL
    SELECT 'campus', 'campus-classroom-moment', 1.0000 UNION ALL
    SELECT 'campus', 'campus-photography', 1.0000 UNION ALL
    SELECT 'campus', 'campus-scholarship', 1.0000 UNION ALL
    SELECT 'campus', 'campus-dorm-makeover', 1.0000 UNION ALL
    SELECT 'campus', 'campus-library-life', 1.0000 UNION ALL
    SELECT 'campus', 'campus-cats', 1.0000 UNION ALL
    SELECT 'campus', 'campus-graduation', 1.0000 UNION ALL
    SELECT 'campus', 'campus-exchange-life', 1.0000 UNION ALL
    SELECT 'campus', 'campus-freshman', 1.0000 UNION ALL
    SELECT 'photography', 'photo-street', 1.0000 UNION ALL
    SELECT 'photography', 'photo-portrait', 1.0000 UNION ALL
    SELECT 'photography', 'photo-film-look', 1.0000 UNION ALL
    SELECT 'photography', 'photo-landscape', 1.0000 UNION ALL
    SELECT 'photography', 'photo-nightscape', 1.0000 UNION ALL
    SELECT 'photography', 'photo-camera-gear', 1.0000 UNION ALL
    SELECT 'photography', 'photo-editing', 1.0000 UNION ALL
    SELECT 'photography', 'photo-composition', 1.0000 UNION ALL
    SELECT 'photography', 'photo-light-shadow', 1.0000 UNION ALL
    SELECT 'photography', 'photo-travel', 1.0000 UNION ALL
    SELECT 'photography', 'photo-citywalk', 1.0000 UNION ALL
    SELECT 'photography', 'photo-mobile', 1.0000 UNION ALL
    SELECT 'photography', 'photo-black-white', 1.0000 UNION ALL
    SELECT 'photography', 'photo-pose', 1.0000 UNION ALL
    SELECT 'photography', 'photo-location', 1.0000 UNION ALL
    SELECT 'photography', 'photo-street-diary', 1.0000 UNION ALL
    SELECT 'photography', 'photo-lens-choice', 1.0000 UNION ALL
    SELECT 'photography', 'photo-raw-editing', 1.0000 UNION ALL
    SELECT 'photography', 'photo-exif-settings', 1.0000 UNION ALL
    SELECT 'photography', 'photo-inspiration', 1.0000 UNION ALL
    SELECT 'pet', 'pet-cat-life', 1.0000 UNION ALL
    SELECT 'pet', 'pet-dog-life', 1.0000 UNION ALL
    SELECT 'pet', 'pet-care-tips', 1.0000 UNION ALL
    SELECT 'pet', 'pet-cute-moment', 1.0000 UNION ALL
    SELECT 'pet', 'pet-adoption-story', 1.0000 UNION ALL
    SELECT 'pet', 'pet-grooming', 1.0000 UNION ALL
    SELECT 'pet', 'pet-health', 1.0000 UNION ALL
    SELECT 'pet', 'pet-new-cat-owner', 1.0000 UNION ALL
    SELECT 'pet', 'pet-new-dog-owner', 1.0000 UNION ALL
    SELECT 'pet', 'pet-products', 1.0000 UNION ALL
    SELECT 'pet', 'pet-multi-pet-home', 1.0000 UNION ALL
    SELECT 'pet', 'pet-photography', 1.0000 UNION ALL
    SELECT 'pet', 'pet-meals', 1.0000 UNION ALL
    SELECT 'pet', 'pet-profile', 1.0000 UNION ALL
    SELECT 'pet', 'pet-travel', 1.0000 UNION ALL
    SELECT 'pet', 'pet-training', 1.0000 UNION ALL
    SELECT 'pet', 'pet-healing-company', 1.0000 UNION ALL
    SELECT 'pet', 'pet-litter-review', 1.0000 UNION ALL
    SELECT 'pet', 'pet-pitfall-notes', 1.0000 UNION ALL
    SELECT 'pet', 'pet-growth', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-expo-diary', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-cos-outfit', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-ita-bag', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-merch-share', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-jp-campus-style', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-lolita-daily', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-jk-uniform', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-character-inspiration', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-makeup-styling', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-figure-display', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-room-setup', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-game-art', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-shooting-scene', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-fan-event', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-hair-color', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-accessory-list', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-home-outfit', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-series-inspiration', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-photo-pose', 1.0000 UNION ALL
    SELECT 'anime_outfit', 'anime-outfit-review', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-application', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-renting', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-campus', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-city-life', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-part-time-job', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-culture-difference', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-packing-list', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-visa', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-language-learning', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-cooking', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-course-selection', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-travel', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-career', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-saving-money', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-social-life', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-dorm-apartment', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-orientation', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-safety', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-return-home', 1.0000 UNION ALL
    SELECT 'overseas_life', 'overseas-emotion', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-tools', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-productivity-workflow', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-dev-tools', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-prompts', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-automation', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-knowledge-management', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-image-generation', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-writing', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-model-review', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-plugin-recommendation', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-office-productivity', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-code-refactor', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-low-code', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-data-analysis', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-learning', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-tool-comparison', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-free-tools', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-workbench-setup', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-news', 1.0000 UNION ALL
    SELECT 'ai_tools', 'ai-tool-pitfalls', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-city-food', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-restaurant-visit', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-cafe', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-budget', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-late-night', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-dessert', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-menu-picks', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-avoid-list', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-breakfast-map', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-hotpot-bbq', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-solo-cooking', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-delivery-review', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-street-snack', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-date-restaurant', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-group-dining', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-drink-review', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-local-cuisine', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-photography', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-save-money', 1.0000 UNION ALL
    SELECT 'food_explore', 'food-new-restaurant', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-weekend-short', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-citywalk', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-guide', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-photo-route', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-hidden-place', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-hiking', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-seaside', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-old-town', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-budget', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-family', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-solo', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-outfit', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-homestay', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-roadtrip', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-exhibition', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-picnic', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-sunset-place', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-pitfalls', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-local-weekend', 1.0000 UNION ALL
    SELECT 'weekend_trip', 'trip-route-review', 1.0000 UNION ALL
    SELECT 'home_life', 'home-rental-makeover', 1.0000 UNION ALL
    SELECT 'home_life', 'home-desk-setup', 1.0000 UNION ALL
    SELECT 'home_life', 'home-storage', 1.0000 UNION ALL
    SELECT 'home_life', 'home-products', 1.0000 UNION ALL
    SELECT 'home_life', 'home-solo-life', 1.0000 UNION ALL
    SELECT 'home_life', 'home-kitchen', 1.0000 UNION ALL
    SELECT 'home_life', 'home-bedroom', 1.0000 UNION ALL
    SELECT 'home_life', 'home-living-room', 1.0000 UNION ALL
    SELECT 'home_life', 'home-cleaning', 1.0000 UNION ALL
    SELECT 'home_life', 'home-fragrance', 1.0000 UNION ALL
    SELECT 'home_life', 'home-small-space', 1.0000 UNION ALL
    SELECT 'home_life', 'home-balcony', 1.0000 UNION ALL
    SELECT 'home_life', 'home-smart-devices', 1.0000 UNION ALL
    SELECT 'home_life', 'home-pitfalls', 1.0000 UNION ALL
    SELECT 'home_life', 'home-color-match', 1.0000 UNION ALL
    SELECT 'home_life', 'home-bookshelf', 1.0000 UNION ALL
    SELECT 'home_life', 'home-bedding-review', 1.0000 UNION ALL
    SELECT 'home_life', 'home-moving', 1.0000 UNION ALL
    SELECT 'home_life', 'home-ritual', 1.0000 UNION ALL
    SELECT 'home_life', 'home-budget-renovation', 1.0000
) seed
JOIN topics t ON t.slug = seed.slug
ON DUPLICATE KEY UPDATE
    weight = VALUES(weight),
    status = VALUES(status);
