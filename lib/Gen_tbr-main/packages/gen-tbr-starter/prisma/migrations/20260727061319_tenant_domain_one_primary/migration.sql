CREATE UNIQUE INDEX "tenant_domain_one_primary_per_tenant"
  ON "tenant_domain" ("tenantId")
  WHERE "isPrimary" = true AND "status" <> 'DETACHED';
