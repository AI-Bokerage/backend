-- V2__modauth_invitations_pre_allocated_user.sql
--
-- Brokerage-owner (TENANT_ADMIN) invitations are created by the platform
-- console (modules/platform), which pre-allocates the owner's future
-- auth_users.id at the same moment it creates the Gen_TNT tenant record —
-- Gen_TNT's own primaryOwnerUserId is exactly this pre-allocated id, opaque
-- to Gen_TNT itself. accept() must create the account with that same id
-- (RegisterService.register's id param is idempotent-safe for exactly this),
-- so the owner's user id lines up across modules/tenant and modules/auth.
-- Null for TEAM_LEAD/BROKER invitations, which still get a fresh random id.

ALTER TABLE modauth_invitations ADD COLUMN IF NOT EXISTS pre_allocated_user_id UUID;
