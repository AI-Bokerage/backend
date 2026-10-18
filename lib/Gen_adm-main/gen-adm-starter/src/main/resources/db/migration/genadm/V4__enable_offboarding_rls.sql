ALTER TABLE offboarding_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE offboarding_jobs FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_offboarding_jobs ON offboarding_jobs
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE offboarding_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE offboarding_steps FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_offboarding_steps ON offboarding_steps
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
