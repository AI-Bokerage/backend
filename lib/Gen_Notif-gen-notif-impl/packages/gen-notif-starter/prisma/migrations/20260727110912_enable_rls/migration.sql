-- Row-level security for every tenant-scoped table.
-- FORCE (not just ENABLE) so the table owner is subject to policies too,
-- and every policy has both USING and WITH CHECK from day one — the
-- source service (notif-svc) shipped USING-only, no FORCE, and had to
-- patch both gaps in a later migration after real exposure. Ship it
-- correct the first time.

ALTER TABLE "notification_preference" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "notification_preference" FORCE ROW LEVEL SECURITY;
CREATE POLICY notification_preference_tenant_isolation ON "notification_preference"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "notification_log" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "notification_log" FORCE ROW LEVEL SECURITY;
CREATE POLICY notification_log_tenant_isolation ON "notification_log"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "digest_queue_entry" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "digest_queue_entry" FORCE ROW LEVEL SECURITY;
CREATE POLICY digest_queue_entry_tenant_isolation ON "digest_queue_entry"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "webhook_endpoint" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "webhook_endpoint" FORCE ROW LEVEL SECURITY;
CREATE POLICY webhook_endpoint_tenant_isolation ON "webhook_endpoint"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- email_suppression is deliberately NOT tenant-scoped (no tenant_id column) — a
-- bounce/complaint suppression is a property of the email address itself.
