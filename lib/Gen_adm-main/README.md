# Gen_ADM

## RBAC (Phase 1)

Full integration instructions: [docs/integration-guide.md](docs/integration-guide.md)

Tenant-scoped roles, a consumer-supplied permission catalog, user-role
assignments, and Postgres RLS for tenant isolation.

- No built-in roles or permissions — supply your own catalog via
  `gen-adm.permissions` in `application.yaml`.
- `UserRoleAssignmentService.bootstrapTenant(...)` is the only way to create
  a fresh tenant's first role — call it in-process from your own
  tenant-provisioning flow, there is no HTTP route for it in the starter
  itself.
- Every other mutating endpoint requires the `adm:roles:manage` permission.
- See `docs/superpowers/specs/2026-07-23-gen-adm-rbac-design.md` for the
  full design.

### Local smoke test

```bash
cd gen-adm-demo
cp .env.example .env
# fill in ADM_DB_URL/ADM_DB_USERNAME/ADM_DB_PASSWORD
../gradlew bootRun &
sleep 5
../scripts/smoke-test.sh
```

## Offboarding Saga

Tenant-scoped, retry-only user offboarding. Session revocation runs as a
mandatory built-in step; add more steps by registering
`OffboardingStepHandler` beans. See `docs/integration-guide.md` for the full
walkthrough.

- `POST /api/v1/offboarding` — initiate (`{ "userId": "...", "reason": "..." }`)
- `GET /api/v1/offboarding/{jobId}` — check status
- `POST /api/v1/offboarding/{jobId}/retry` — manually retry a FAILED job

A consumer **must** provide a `SessionRevocationGateway` bean — there is no
default, and startup fails without one.

## Impersonation Requests

Tenant-scoped, intra-tenant impersonation consent workflow: a privileged
user requests to act as another user in the same tenant, a second
privileged user grants or denies consent, and the resulting active session
can be ended by either the impersonator or an admin. See
`docs/integration-guide.md` for the full walkthrough.

- `POST /api/v1/impersonation-requests` — request (`{ "targetUserId": "...", "reason": "...", "ttlMinutes": 30 }`), requires `adm:impersonation:request`
- `GET /api/v1/impersonation-requests` — list pending/active sessions, requires `adm:impersonation:manage`
- `POST /api/v1/impersonation-requests/{id}/approve` / `/reject` — requires `adm:impersonation:manage`; the reviewer cannot be the requester
- `POST /api/v1/impersonation-requests/{id}/end` — the impersonator can end their own session; anyone else needs `adm:impersonation:manage`

Gen_ADM only tracks consent and session state — it does not mint or scope
any token for actually acting as the impersonated user; that execution step
is the consuming application's responsibility.

## Invitations

Tenant-scoped member invitations: create an invitation for an email with a
set of roles, the invitee accepts with a one-time token (no login
required — the token itself is the authentication), and the roles are
assigned to a userId the invitee's own signup/auth flow already created.
See `docs/integration-guide.md` for the full walkthrough.

- `POST /api/v1/invitations` — create (`{ "email": "...", "roleIds": ["..."] }`), requires `adm:invitations:manage`. The response's `token` field is shown only this once.
- `GET /api/v1/invitations` — list pending invitations, requires `adm:invitations:manage`
- `POST /api/v1/invitations/{id}/cancel` — requires `adm:invitations:manage`
- `POST /api/v1/invitations/{token}/accept` — public, `{ "userId": "..." }`; no permission or authenticated principal required

Gen_ADM does not create the invited user's account or send the invitation
email — it returns the plaintext token in the create response and fires an
optional `InvitationEventPublisher.onCreated(...)` hook (no-op by default)
so the consuming application can deliver it however it wants.

## Support Tickets

Tenant-scoped support ticket requests with a real lifecycle. Any
authenticated tenant member can file a ticket and check on their own; an
admin can see every ticket in the tenant and move one through its
lifecycle. See `docs/integration-guide.md` for the full walkthrough.

- `POST /api/v1/support-tickets` — file a ticket (`{ "type": "...", "subject": "...", "description": "...", "priority": "..." }`), any authenticated principal, no permission required
- `GET /api/v1/support-tickets/mine` — list your own tickets, any authenticated principal, no permission required
- `GET /api/v1/support-tickets` — list every ticket in the tenant, requires `adm:tickets:manage`
- `POST /api/v1/support-tickets/{id}/start` / `/resolve` / `/close` — move a ticket through `OPEN → IN_PROGRESS → RESOLVED → CLOSED`; requires `adm:tickets:manage`. `close` works from any non-`CLOSED` state, not only from `RESOLVED`.

Gen_ADM only tracks the ticket record and its lifecycle — it does not
provide a real support desk (no comments, attachments, or agent
assignment). Register an `TicketEventPublisher` bean to forward new
tickets into whatever support tool you actually use; the default is a
no-op.

## Data Export Jobs

Tenant-scoped, synchronous export of everything Gen_ADM owns for a tenant —
roles (with their permission codes), user↔role assignments, offboarding
jobs, impersonation sessions, invitations, and support tickets — as one
JSON snapshot. See `docs/integration-guide.md` for the full walkthrough.

- `POST /api/v1/exports` — generate a new export immediately (no request body), requires `adm:exports:manage`
- `GET /api/v1/exports` — list every export for the tenant, requires `adm:exports:manage`
- `GET /api/v1/exports/{id}` — get one export's status/metadata, requires `adm:exports:manage`
- `GET /api/v1/exports/{id}/download` — download the raw JSON snapshot, requires `adm:exports:manage`. 409 if not `COMPLETED` (already `EXPIRED` or `REVOKED`).
- `POST /api/v1/exports/{id}/revoke` — delete the stored snapshot early, requires `adm:exports:manage`. 409 if not `COMPLETED`.

Exports generate synchronously — there is no approval step and no queued
generation, unlike CPMS's Super-Admin-gated workflow. Each export expires
automatically after `gen-adm.export-ttl-days` (default 7) the next time it
is read, at which point its snapshot content is cleared.
