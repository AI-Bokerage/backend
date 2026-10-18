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

DROP POLICY notification_preference_tenant_isolation ON "notification_preference";
CREATE POLICY notification_preference_tenant_isolation ON "notification_preference"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

DROP POLICY notification_log_tenant_isolation ON "notification_log";
CREATE POLICY notification_log_tenant_isolation ON "notification_log"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

DROP POLICY digest_queue_entry_tenant_isolation ON "digest_queue_entry";
CREATE POLICY digest_queue_entry_tenant_isolation ON "digest_queue_entry"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);

DROP POLICY webhook_endpoint_tenant_isolation ON "webhook_endpoint";
CREATE POLICY webhook_endpoint_tenant_isolation ON "webhook_endpoint"
    USING      (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
