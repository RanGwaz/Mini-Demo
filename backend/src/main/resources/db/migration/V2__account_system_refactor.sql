-- =============================================================
-- V2: 用户账号体系重构
-- 设计原则：
--   1. 不删除已有字段，仅 ADD COLUMN / ADD INDEX / DROP FOREIGN KEY
--   2. 全面移除外键约束，用索引替代，适配高并发分库分表场景
--   3. 所有表补充 deleted(逻辑删除) + version(乐观锁)
--   4. users 表新增安全相关字段及第三方登录扩展表
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 1. users 表：新增字段
-- -------------------------------------------------------------

-- user_no: 对外展示的用户唯一编号（8位，字母/数字/-/_），区别于内部自增 id
--   高并发下不暴露 id 序列，防止用户数量被枚举
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS user_no              VARCHAR(16)   NULL COMMENT '对外用户唯一编号，8位字母数字-_，唯一'              AFTER username,
    ADD COLUMN IF NOT EXISTS user_no_updated_at   DATETIME      NULL COMMENT '最近一次修改 user_no 的时间，用于控制半年一次限制'    AFTER user_no,
    ADD COLUMN IF NOT EXISTS phone_hash           VARCHAR(64)   NULL COMMENT '手机号 SHA-256(phone + salt)，用于唯一性校验'       AFTER bio,
    ADD COLUMN IF NOT EXISTS phone_salt           VARCHAR(32)   NULL COMMENT '手机号哈希随机盐，每次绑定时重新生成'               AFTER phone_hash,
    ADD COLUMN IF NOT EXISTS last_login_at        DATETIME      NULL COMMENT '最后登录时间，登录成功后异步更新'                   AFTER phone_salt,
    ADD COLUMN IF NOT EXISTS login_ip             VARCHAR(64)   NULL COMMENT '最后登录 IP（支持 IPv6，最长 45 字符）'            AFTER last_login_at,
    ADD COLUMN IF NOT EXISTS deleted              TINYINT(1)    NOT NULL DEFAULT 0  COMMENT '逻辑删除：0=正常 1=已删除'          AFTER login_ip,
    ADD COLUMN IF NOT EXISTS version              INT           NOT NULL DEFAULT 0  COMMENT '乐观锁版本号，每次更新 +1'           AFTER deleted;

-- user_no 唯一索引
ALTER TABLE users
    ADD UNIQUE INDEX uk_users_user_no (user_no);

-- phone_hash 唯一索引（NULL 值不参与唯一约束，允许未绑定手机的用户）
ALTER TABLE users
    ADD UNIQUE INDEX uk_users_phone_hash (phone_hash);

-- 逻辑删除 + 状态联合查询索引（高频：列表查询过滤已删除用户）
ALTER TABLE users
    ADD INDEX idx_users_status_deleted (status, deleted);

-- 最后登录时间索引（运营后台按活跃度排序）
ALTER TABLE users
    ADD INDEX idx_users_last_login_at (last_login_at);

-- -------------------------------------------------------------
-- 2. 第三方登录扩展表 user_oauth
--    独立表设计：一个账号可绑定多个第三方平台，解耦主表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_oauth (
    id          BIGINT        NOT NULL AUTO_INCREMENT         COMMENT '主键',
    user_id     BIGINT        NOT NULL                        COMMENT '关联 users.id（无外键，应用层保证）',
    provider    VARCHAR(32)   NOT NULL                        COMMENT '第三方平台标识：wechat / github / google / apple 等',
    open_id     VARCHAR(128)  NOT NULL                        COMMENT '第三方平台用户唯一标识（openid / sub）',
    union_id    VARCHAR(128)  NULL                            COMMENT '微信 union_id 等跨应用唯一 ID',
    access_token  VARCHAR(512) NULL                           COMMENT '第三方 access_token（可选存储，加密后存）',
    expires_at  DATETIME      NULL                            COMMENT 'access_token 过期时间',
    raw_info    TEXT          NULL                            COMMENT '第三方返回的原始 JSON 信息快照',
    deleted     TINYINT(1)    NOT NULL DEFAULT 0              COMMENT '逻辑删除',
    version     INT           NOT NULL DEFAULT 0              COMMENT '乐观锁',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户第三方OAuth绑定表';

-- (provider, open_id) 联合唯一：同平台同 open_id 只能绑定一个账号
ALTER TABLE user_oauth
    ADD UNIQUE INDEX uk_oauth_provider_openid (provider, open_id);

-- 通过 user_id 查询某用户绑定了哪些第三方平台
ALTER TABLE user_oauth
    ADD INDEX idx_oauth_user_id (user_id);

-- -------------------------------------------------------------
-- 3. 移除所有外键约束
--    外键在高并发写入时会造成行级锁传播，不适合互联网系统
--    关联一致性由应用层 + 定期数据校验任务保证
-- -------------------------------------------------------------

-- posts
ALTER TABLE posts        DROP FOREIGN KEY IF EXISTS fk_posts_author;

-- post_assets
ALTER TABLE post_assets  DROP FOREIGN KEY IF EXISTS fk_post_assets_post;

-- user_follows
ALTER TABLE user_follows DROP FOREIGN KEY IF EXISTS fk_user_follows_follower;
ALTER TABLE user_follows DROP FOREIGN KEY IF EXISTS fk_user_follows_followed;

-- post_likes
ALTER TABLE post_likes   DROP FOREIGN KEY IF EXISTS fk_post_likes_user;
ALTER TABLE post_likes   DROP FOREIGN KEY IF EXISTS fk_post_likes_post;

-- post_favorites
ALTER TABLE post_favorites DROP FOREIGN KEY IF EXISTS fk_post_favorites_user;
ALTER TABLE post_favorites DROP FOREIGN KEY IF EXISTS fk_post_favorites_post;

-- post_comments
ALTER TABLE post_comments  DROP FOREIGN KEY IF EXISTS fk_post_comments_user;
ALTER TABLE post_comments  DROP FOREIGN KEY IF EXISTS fk_post_comments_post;

-- user_blocks
ALTER TABLE user_blocks    DROP FOREIGN KEY IF EXISTS fk_user_blocks_user;
ALTER TABLE user_blocks    DROP FOREIGN KEY IF EXISTS fk_user_blocks_target;

-- post_negative_feedbacks
ALTER TABLE post_negative_feedbacks DROP FOREIGN KEY IF EXISTS fk_post_negative_feedbacks_user;
ALTER TABLE post_negative_feedbacks DROP FOREIGN KEY IF EXISTS fk_post_negative_feedbacks_post;

-- content_reports
ALTER TABLE content_reports DROP FOREIGN KEY IF EXISTS fk_content_reports_user;
ALTER TABLE content_reports DROP FOREIGN KEY IF EXISTS fk_content_reports_post;

-- -------------------------------------------------------------
-- 4. 所有关联表补充 deleted + version + 必要索引
-- -------------------------------------------------------------

-- posts
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER updated_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;
ALTER TABLE posts
    ADD INDEX IF NOT EXISTS idx_posts_author_deleted (author_id, deleted),
    ADD INDEX IF NOT EXISTS idx_posts_audit_deleted  (audit_status, deleted);

-- post_assets
ALTER TABLE post_assets
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER created_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;
ALTER TABLE post_assets
    ADD INDEX IF NOT EXISTS idx_post_assets_post_id (post_id);

-- user_follows
ALTER TABLE user_follows
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER created_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;
-- 反向查询：查询某人的粉丝
ALTER TABLE user_follows
    ADD INDEX IF NOT EXISTS idx_user_follows_followed_id (followed_id);

-- post_likes
ALTER TABLE post_likes
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER created_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;
ALTER TABLE post_likes
    ADD INDEX IF NOT EXISTS idx_post_likes_post_id (post_id);

-- post_favorites
ALTER TABLE post_favorites
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER created_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;
ALTER TABLE post_favorites
    ADD INDEX IF NOT EXISTS idx_post_favorites_post_id (post_id);

-- post_comments
ALTER TABLE post_comments
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER created_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;

-- user_blocks
ALTER TABLE user_blocks
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER created_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;
ALTER TABLE user_blocks
    ADD INDEX IF NOT EXISTS idx_user_blocks_blocked (blocked_user_id);

-- post_negative_feedbacks
ALTER TABLE post_negative_feedbacks
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER created_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;
ALTER TABLE post_negative_feedbacks
    ADD INDEX IF NOT EXISTS idx_pnf_post_id (post_id);

-- content_reports
ALTER TABLE content_reports
    ADD COLUMN IF NOT EXISTS deleted  TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER created_at,
    ADD COLUMN IF NOT EXISTS version  INT        NOT NULL DEFAULT 0 COMMENT '乐观锁'   AFTER deleted;
ALTER TABLE content_reports
    ADD INDEX IF NOT EXISTS idx_content_reports_post_id    (post_id);
ALTER TABLE content_reports
    ADD INDEX IF NOT EXISTS idx_content_reports_reporter   (reporter_id);

-- user_events（写多读少，不加 deleted/version，保持轻量）
-- 补充复合索引以支持推荐系统按类型查询
ALTER TABLE user_events
    ADD INDEX IF NOT EXISTS idx_user_events_type_target (event_type, target_type, target_id);

SET FOREIGN_KEY_CHECKS = 1;
