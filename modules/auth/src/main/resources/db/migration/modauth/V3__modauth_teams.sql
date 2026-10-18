-- V3__modauth_teams.sql
--
-- First-class Team entity for the Tenant Admin "People/Teams" flow: a team
-- is a name + one Team Lead + a set of Brokers. Previously `team_name` on
-- modauth_user_roles/modauth_invitations was free text with no shared
-- identity across members — this adds the real row teams need (add/remove
-- members, block delete while members exist) while keeping team_name as a
-- denormalized display snapshot for invitations made before a team exists.

CREATE TABLE IF NOT EXISTS modauth_teams (
  id                 UUID                     NOT NULL,
  tenant_id          UUID                     NOT NULL,
  name               CHARACTER VARYING(255)   NOT NULL,
  team_lead_user_id  UUID                     NOT NULL,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT modauth_teams_pkey PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS idx_modauth_teams_tenant ON modauth_teams (tenant_id);

-- Which team (if any) this member currently belongs to. Nullable: a Team
-- Lead has none until a team is created for them; a removed Broker goes
-- back to null without losing their role or clients.
ALTER TABLE modauth_user_roles ADD COLUMN IF NOT EXISTS team_id UUID;
ALTER TABLE modauth_invitations ADD COLUMN IF NOT EXISTS team_id UUID;

-- Invitee's display name, snapshotted at accept time for the People list —
-- previously only lived on the (now-accepted, effectively archival) invitation row.
ALTER TABLE modauth_user_roles ADD COLUMN IF NOT EXISTS name CHARACTER VARYING(255);
