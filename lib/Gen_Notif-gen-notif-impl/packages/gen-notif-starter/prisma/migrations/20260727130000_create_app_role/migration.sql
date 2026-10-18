-- Non-superuser application role, so RLS is actually enforced at runtime.
--
-- docker-compose's POSTGRES_USER=gennotif is the initdb bootstrap role, and
-- Postgres always makes that role a superuser. Superusers bypass row-level
-- security unconditionally, even with FORCE ROW LEVEL SECURITY — so every
-- connection made as gennotif/gennotif bypasses the tenant-isolation
-- policies from the enable_rls migration entirely. Create a dedicated,
-- unprivileged role for the running application to connect as instead.
-- `gennotif` (or another privileged role) still runs migrations — CREATE
-- POLICY / ALTER TABLE ... FORCE ROW LEVEL SECURITY need elevated
-- privileges this app role deliberately does not have.
--
-- Local-dev credential only (this docker-compose Postgres isn't exposed
-- beyond localhost) — reuses the gennotif/gennotif/gennotif password
-- convention already in this project's .env.example.
--
-- SECURITY: this migration ships inside the published npm package (see
-- prisma/migrations in this package's package.json "files") and creates
-- gennotif_app with a well-known default password ('gennotif_app'), matching
-- this repo's local-dev convention (see docker-compose.yml / .env.example).
-- Before deploying against any non-local Postgres instance, rotate it:
--   ALTER ROLE gennotif_app WITH PASSWORD '<a real secret, not this one>';
-- Do NOT run this migration as-is against a shared or production database
-- without rotating the password immediately after.

DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'gennotif_app') THEN
    CREATE ROLE gennotif_app LOGIN PASSWORD 'gennotif_app';
  END IF;
END $$;

GRANT USAGE ON SCHEMA public TO gennotif_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON
  "notification_preference",
  "notification_log",
  "digest_queue_entry",
  "webhook_endpoint",
  "email_suppression"
TO gennotif_app;

-- No sequence GRANTs: every id column uses
-- `@default(dbgenerated("gen_random_uuid()"))`, not a serial/identity
-- sequence, so there is no sequence object gennotif_app needs access to.

-- Deliberately NOT granted: BYPASSRLS, SUPERUSER, or membership in gennotif
-- — the entire point of this role is that it stays subject to RLS.
