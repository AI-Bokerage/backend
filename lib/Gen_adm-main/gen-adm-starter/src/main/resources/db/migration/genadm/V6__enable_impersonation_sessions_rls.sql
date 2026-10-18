ALTER TABLE impersonation_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE impersonation_sessions FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_impersonation_sessions ON impersonation_sessions
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
