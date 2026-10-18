-- Row-level security for the two tenant-scoped tables. FORCE (not just
-- ENABLE) so the table owner is subject to policies too. NULLIF guards
-- against the Postgres "custom GUC resets to '' not NULL on pooled
-- connections" gotcha from day one (see Gen_NOTIF/Gen_USG history for why):
-- withTenant() runs `SET LOCAL app.tenant_id` inside a transaction; once
-- that transaction commits, a reused pooled connection's next query outside
-- withTenant() would otherwise hit `current_setting(...)::uuid` casting ''
-- to uuid and throwing 22P02 instead of degrading to "no tenant set -> zero
-- rows". NULLIF(..., '') turns that placeholder default into NULL first.

ALTER TABLE "tenant_feature_flag" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "tenant_feature_flag" FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_feature_flag_tenant_isolation ON "tenant_feature_flag"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

ALTER TABLE "feature_usage_event" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "feature_usage_event" FORCE ROW LEVEL SECURITY;
CREATE POLICY feature_usage_event_tenant_isolation ON "feature_usage_event"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

-- module/plan_module/feature_flag are deliberately NOT tenant-scoped (no
-- tenant_id column at all) — global catalog data, identical for every tenant.
-- Confirmed correct against fmm-svc source and the CPMS decision matrix.
