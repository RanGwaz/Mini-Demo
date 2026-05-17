--liquibase formatted sql

--changeset rangwaz:070-001-drop-removed-admin-tables
--comment Remove tables that only served the removed admin/enterprise modules.
DROP TABLE IF EXISTS content_import_items;
DROP TABLE IF EXISTS content_import_batches;
DROP TABLE IF EXISTS admin_operation_logs;
DROP TABLE IF EXISTS content_rebuild_tasks;

DROP TABLE IF EXISTS offline_eval_reports;
DROP TABLE IF EXISTS model_versions;
DROP TABLE IF EXISTS training_datasets;

DROP TABLE IF EXISTS account_moderation_actions;
DROP TABLE IF EXISTS content_moderation_cases;
DROP TABLE IF EXISTS creator_profiles;
DROP TABLE IF EXISTS commercial_content_profiles;

DROP TABLE IF EXISTS recommendation_experiments;
DROP TABLE IF EXISTS recommendation_source_snapshots;
