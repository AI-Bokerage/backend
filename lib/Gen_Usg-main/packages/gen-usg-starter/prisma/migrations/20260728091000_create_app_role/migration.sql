-- Non-superuser application role, so RLS is actually enforced at runtime.
--
-- docker-compose's POSTGRES_USER=genusg is the initdb bootstrap role, and
-- Postgres always makes that role a superuser. Superusers bypass row-level
-- security unconditionally, even with FORCE ROW LEVEL SECURITY — so every
-- connection made as genusg/genusg bypasses the tenant-isolation policies
-- from the enable_rls migration entirely. Create a dedicated, unprivileged
-- role for the running application to connect as instead. `genusg` (or
-- another privileged role) still runs migrations — CREATE POLICY / ALTER
-- TABLE ... FORCE ROW LEVEL SECURITY need elevated privileges this app role
-- deliberately does not have.
--
-- Local-dev credential only (this docker-compose Postgres isn't exposed
-- beyond localhost) — reuses the genusg/genusg/genusg password convention
-- already in this project's .env.example.
--
-- SECURITY: this migration ships inside the published npm package (see
-- prisma/migrations in this package's package.json "files") and creates
-- genusg_app with a well-known default password ('genusg_app'), matching
-- this repo's local-dev convention (see docker-compose.yml / .env.example).
-- Before deploying against any non-local Postgres instance, rotate it:
--   ALTER ROLE genusg_app WITH PASSWORD '<a real secret, not this one>';
-- Do NOT run this migration as-is against a shared or production database
-- without rotating the password immediately after.

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'genusg_app') THEN
    CREATE ROLE genusg_app LOGIN PASSWORD 'genusg_app';
  END IF;
END $$;

GRANT USAGE ON SCHEMA public TO genusg_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON
  "usage_snapshot",
  "usage_grace_overage",
  "usage_reconciliation_log",
  "usage_idempotency_ledger"
TO genusg_app;

-- usage_idempotency_ledger has no RLS policy (no tenant_id column, by
-- design), but IncrementService's idempotency repo still reads/writes it
-- as this app role, so it needs the same grants as the tenant-scoped tables.

-- No sequence GRANTs: every id column uses
-- `@default(dbgenerated("gen_random_uuid()"))`, not a serial/identity
-- sequence, so there is no sequence object genusg_app needs access to.

-- Deliberately NOT granted: BYPASSRLS, SUPERUSER, or membership in genusg
-- — the entire point of this role is that it stays subject to RLS.
