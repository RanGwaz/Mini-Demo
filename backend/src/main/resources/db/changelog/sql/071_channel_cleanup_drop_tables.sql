--liquibase formatted sql

--changeset rangwaz:071-001-drop-removed-channel-tables
--comment Remove database tables that only served the removed channel navigation/API layer.
DROP TABLE IF EXISTS topic_channel_bindings;
DROP TABLE IF EXISTS channels;
