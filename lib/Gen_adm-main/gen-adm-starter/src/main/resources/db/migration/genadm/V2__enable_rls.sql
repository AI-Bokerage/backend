ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_roles ON roles
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE user_role_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_role_assignments FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_user_role_assignments ON user_role_assignments
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- permissions stays global (no tenant_id column) — not policed by RLS.
