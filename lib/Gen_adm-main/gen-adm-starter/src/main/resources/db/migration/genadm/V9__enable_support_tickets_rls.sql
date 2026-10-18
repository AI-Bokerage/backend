ALTER TABLE support_tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE support_tickets FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_support_tickets ON support_tickets
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);
