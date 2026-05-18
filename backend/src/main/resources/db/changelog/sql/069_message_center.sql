--liquibase formatted sql

--changeset rangwaz:069-001-create-site-messages
CREATE TABLE IF NOT EXISTS site_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sender_id BIGINT NULL,
    recipient_id BIGINT NOT NULL,
    peer_id BIGINT NULL,
    message_kind VARCHAR(32) NOT NULL DEFAULT 'DIRECT',
    title VARCHAR(160) NULL,
    content VARCHAR(1000) NOT NULL,
    action_url VARCHAR(512) NULL,
    read_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_site_messages_recipient_kind_created (recipient_id, message_kind, created_at),
    INDEX idx_site_messages_recipient_read_kind (recipient_id, read_at, message_kind),
    INDEX idx_site_messages_direct_thread (sender_id, recipient_id, message_kind, created_at),
    INDEX idx_site_messages_action (action_url)
);
