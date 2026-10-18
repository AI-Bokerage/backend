ALTER TABLE data_exports ENABLE ROW LEVEL SECURITY;
ALTER TABLE data_exports FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_data_exports ON data_exports
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
