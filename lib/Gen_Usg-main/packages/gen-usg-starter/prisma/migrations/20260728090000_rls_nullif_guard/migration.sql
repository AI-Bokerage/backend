-- Guard the RLS policies against the Postgres "custom GUC resets to '' not
-- NULL" pooling gotcha.
--
-- withTenant() runs `SET LOCAL app.tenant_id = '<uuid>'` inside a transaction.
-- When that transaction commits, Postgres resets app.tenant_id back to its
-- prior value on that backend connection. For a custom (unregistered) GUC
-- that has never been set at session level before, the "prior value" it
-- resets to is the empty string placeholder default, not NULL. On a reused
-- pooled connection, the very next query that runs outside withTenant() then
-- hits `current_setting('app.tenant_id', true)::uuid` casting '' to uuid,
-- which throws 22P02 instead of the intended "no tenant set -> zero rows"
-- behavior. NULLIF(..., '') turns that placeholder default into NULL first,
-- so the comparison degrades to `tenant_id = NULL` (no match, zero rows,
-- no error) — which is what every one of these policies was meant to do.

DROP POLICY usage_snapshot_tenant_isolation ON "usage_snapshot";
CREATE POLICY usage_snapshot_tenant_isolation ON "usage_snapshot"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

DROP POLICY usage_grace_overage_tenant_isolation ON "usage_grace_overage";
CREATE POLICY usage_grace_overage_tenant_isolation ON "usage_grace_overage"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

DROP POLICY usage_reconciliation_log_tenant_isolation ON "usage_reconciliation_log";
CREATE POLICY usage_reconciliation_log_tenant_isolation ON "usage_reconciliation_log"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
