-- Non-superuser application role, so RLS is actually enforced at runtime.
--
-- docker-compose's POSTGRES_USER=genfmm is the initdb bootstrap role, and
-- Postgres always makes that role a superuser. Superusers bypass row-level
-- security unconditionally, even with FORCE ROW LEVEL SECURITY — so every
-- connection made as genfmm/genfmm bypasses the tenant-isolation policies
-- from the enable_rls migration entirely. Create a dedicated, unprivileged
-- role for the running application to connect as instead. `genfmm` (or
-- another privileged role) still runs migrations — CREATE POLICY / ALTER
-- TABLE ... FORCE ROW LEVEL SECURITY need elevated privileges this app role
-- deliberately does not have.
--
-- Local-dev credential only (this docker-compose Postgres isn't exposed
-- beyond localhost) — reuses the genfmm/genfmm/genfmm password convention
-- already in this project's .env.example.
--
-- SECURITY: this migration ships inside the published npm package (see
-- prisma/migrations in this package's package.json "files") and creates
-- genfmm_app with a well-known default password ('genfmm_app'). Before
-- deploying against any non-local Postgres instance, rotate it:
--   ALTER ROLE genfmm_app WITH PASSWORD '<a real secret, not this one>';
-- Do NOT run this migration as-is against a shared or production database
-- without rotating the password immediately after.

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'genfmm_app') THEN
    CREATE ROLE genfmm_app LOGIN PASSWORD 'genfmm_app';
  END IF;
END $$;

GRANT USAGE ON SCHEMA public TO genfmm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON
  "module",
  "plan_module",
  "feature_flag",
  "tenant_feature_flag",
  "feature_usage_event"
TO genfmm_app;

-- No sequence GRANTs: every id column uses either a natural-key varchar PK
-- or `@default(dbgenerated("gen_random_uuid()"))`, never a serial/identity
-- sequence.

-- Deliberately NOT granted: BYPASSRLS, SUPERUSER, or membership in genfmm —
-- the entire point of this role is that it stays subject to RLS.
