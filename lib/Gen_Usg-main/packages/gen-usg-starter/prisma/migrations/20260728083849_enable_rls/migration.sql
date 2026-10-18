-- Row-level security for every tenant-scoped table.
-- FORCE (not just ENABLE) so the table owner is subject to policies too,
-- and every policy has both USING and WITH CHECK from day one — matches
-- every other Gen_MS sibling's RLS convention.

ALTER TABLE "usage_snapshot" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "usage_snapshot" FORCE ROW LEVEL SECURITY;
CREATE POLICY usage_snapshot_tenant_isolation ON "usage_snapshot"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "usage_grace_overage" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "usage_grace_overage" FORCE ROW LEVEL SECURITY;
CREATE POLICY usage_grace_overage_tenant_isolation ON "usage_grace_overage"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE "usage_reconciliation_log" ENABLE ROW LEVEL SECURITY;
ALTER TABLE "usage_reconciliation_log" FORCE ROW LEVEL SECURITY;
CREATE POLICY usage_reconciliation_log_tenant_isolation ON "usage_reconciliation_log"
    USING      (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- usage_idempotency_ledger is deliberately NOT tenant-scoped (no tenant_id
-- column) — it's a pure event-id dedup backstop, not a tenant-queryable
-- resource. No RLS policy applies to it.
