-- V011: Add limit fields to ppm_plan_versions (PPM-12B)
--
-- These 7 fields live in BSM's local plan_versions today.
-- Adding them to PPM makes PPM the authoritative source for plan limits,
-- enabling BSM's executeDowngradePreflight to call PPM /limits instead of
-- reading from its own catalog.  BSM's local plan_versions will be dropped
-- in a future BSM migration once all reads are migrated to PPM.
--
-- All columns are nullable: null means "unlimited" for numeric caps and false
-- for boolean feature flags.

ALTER TABLE ppm_plan_versions
    ADD COLUMN max_internal_users   INTEGER      NULL,
    ADD COLUMN max_client_users     INTEGER      NULL,
    ADD COLUMN max_active_projects  INTEGER      NULL,
    ADD COLUMN storage_quota_bytes  BIGINT       NULL,
    ADD COLUMN custom_domain_enabled BOOLEAN     NULL,
    ADD COLUMN sso_enabled          BOOLEAN      NULL,
    ADD COLUMN priority_support     BOOLEAN      NULL;
