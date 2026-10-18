-- V6__modauth_tenant_access_and_profile.sql
--
-- Flow 1 "Suspend → everyone in that brokerage is blocked at login": the
-- platform tells this service when a brokerage is suspended, reactivated or
-- cancelled (PUT /internal/modauth/tenants/{id}/access). No row = allowed.
CREATE TABLE IF NOT EXISTS modauth_tenant_access (
  tenant_id   UUID                     NOT NULL,
  blocked     BOOLEAN                  NOT NULL,
  status      CHARACTER VARYING(20)    NOT NULL,
  updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT modauth_tenant_access_pkey PRIMARY KEY (tenant_id)
);

-- Flow 4.12 "My profile" — name already lives here; phone is new.
ALTER TABLE modauth_user_roles ADD COLUMN IF NOT EXISTS phone CHARACTER VARYING(50);
