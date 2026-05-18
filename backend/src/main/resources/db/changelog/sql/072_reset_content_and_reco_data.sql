--liquibase formatted sql

--changeset rangwaz:072-001-disable-fk-for-data-reset
SET FOREIGN_KEY_CHECKS = 0;

--changeset rangwaz:072-010-truncate-site-messages
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'site_messages'
TRUNCATE TABLE site_messages;

--changeset rangwaz:072-011-truncate-feed-impression-logs
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feed_impression_logs'
TRUNCATE TABLE feed_impression_logs;

--changeset rangwaz:072-012-truncate-feed-request-logs
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'feed_request_logs'
TRUNCATE TABLE feed_request_logs;

--changeset rangwaz:072-013-truncate-post-i2i-neighbors
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_i2i_neighbors'
TRUNCATE TABLE post_i2i_neighbors;

--changeset rangwaz:072-014-truncate-recommendation-tag-dictionary
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recommendation_tag_dictionary'
TRUNCATE TABLE recommendation_tag_dictionary;

--changeset rangwaz:072-015-truncate-post-features
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_features'
TRUNCATE TABLE post_features;

--changeset rangwaz:072-016-truncate-user-features
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_features'
TRUNCATE TABLE user_features;

--changeset rangwaz:072-017-truncate-user-events
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_events'
TRUNCATE TABLE user_events;

--changeset rangwaz:072-018-truncate-user-interest-subscriptions
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_interest_subscriptions'
TRUNCATE TABLE user_interest_subscriptions;

--changeset rangwaz:072-019-truncate-user-topic-follows
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_topic_follows'
TRUNCATE TABLE user_topic_follows;

--changeset rangwaz:072-020-truncate-post-topics
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_topics'
TRUNCATE TABLE post_topics;

--changeset rangwaz:072-021-truncate-topic-trend-snapshots
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topic_trend_snapshots'
TRUNCATE TABLE topic_trend_snapshots;

--changeset rangwaz:072-022-truncate-topic-merge-logs
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topic_merge_logs'
TRUNCATE TABLE topic_merge_logs;

--changeset rangwaz:072-023-truncate-topic-aliases
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topic_aliases'
TRUNCATE TABLE topic_aliases;

--changeset rangwaz:072-024-truncate-topic-clusters
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topic_clusters'
TRUNCATE TABLE topic_clusters;

--changeset rangwaz:072-025-truncate-topics
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'topics'
TRUNCATE TABLE topics;

--changeset rangwaz:072-030-truncate-content-reports
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'content_reports'
TRUNCATE TABLE content_reports;

--changeset rangwaz:072-031-truncate-post-negative-feedbacks
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_negative_feedbacks'
TRUNCATE TABLE post_negative_feedbacks;

--changeset rangwaz:072-032-truncate-post-comments
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_comments'
TRUNCATE TABLE post_comments;

--changeset rangwaz:072-033-truncate-post-likes
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_likes'
TRUNCATE TABLE post_likes;

--changeset rangwaz:072-034-truncate-post-favorites
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_favorites'
TRUNCATE TABLE post_favorites;

--changeset rangwaz:072-035-truncate-post-assets
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'post_assets'
TRUNCATE TABLE post_assets;

--changeset rangwaz:072-036-truncate-posts
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'posts'
TRUNCATE TABLE posts;

--changeset rangwaz:072-040-truncate-user-follows
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_follows'
TRUNCATE TABLE user_follows;

--changeset rangwaz:072-041-truncate-user-blocks
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'user_blocks'
TRUNCATE TABLE user_blocks;

--changeset rangwaz:072-999-enable-fk-after-data-reset
SET FOREIGN_KEY_CHECKS = 1;
