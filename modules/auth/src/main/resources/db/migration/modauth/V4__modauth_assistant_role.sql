-- V4__modauth_assistant_role.sql
--
-- Adds ASSISTANT (SOW Module 2.5) to both role check constraints — supports
-- a team like a Broker does, with no own book of leads to originate.

ALTER TABLE modauth_user_roles DROP CONSTRAINT modauth_user_roles_role_check;
ALTER TABLE modauth_user_roles ADD CONSTRAINT modauth_user_roles_role_check CHECK (
  role IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'TEAM_LEAD', 'BROKER', 'ASSISTANT')
);

ALTER TABLE modauth_invitations DROP CONSTRAINT modauth_invitations_role_check;
ALTER TABLE modauth_invitations ADD CONSTRAINT modauth_invitations_role_check CHECK (
  role IN ('TENANT_ADMIN', 'TEAM_LEAD', 'BROKER', 'ASSISTANT')
);
