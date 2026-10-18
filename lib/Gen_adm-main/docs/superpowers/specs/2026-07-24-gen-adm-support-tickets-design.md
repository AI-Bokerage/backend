# Gen_ADM Support Tickets — Design

## Why

`support tickets` was explicitly deferred in the RBAC design spec
([[2026-07-23-gen-adm-rbac-design]]) as CPMS-specific: in CPMS-Platform's
`adm-svc`, a support ticket request is a pure fire-and-forget proxy —
`SupportTicketServiceImpl.requestTicket(...)` forwards
`type`/`subject`/`description`/`priority` straight to a separate SUP-SVC via
`SupSvcClient.createTicket(...)` and returns `{ticketId, status: "ACCEPTED"}`
(`pre-context/adm-svc/src/main/java/com/example/admsvc/application/impl/SupportTicketServiceImpl.java`).
There is no read, no list, and no status lifecycle anywhere in `adm-svc` —
SUP-SVC owns everything past creation. This is the thinnest of the four
CPMS-deferred features ported so far (offboarding, impersonation,
invitations); the source gives almost no lifecycle to port, so this spec is
more green-field than the prior three.

Following the same move already made for offboarding/impersonation/
invitations, Gen_ADM owns the ticket record itself rather than proxying to
an external service it doesn't have. Unlike CPMS, Gen_ADM's version has a
real status lifecycle (`OPEN → IN_PROGRESS → RESOLVED → CLOSED`) — every
other Gen_ADM feature this session has one, and a ticket permanently stuck
as "just created" would be the odd one out.

## Scope

**In scope:**
- `SupportTicketEntity` — own table, tenant-scoped RLS (same pattern as
  offboarding/impersonation; unlike invitations, there is no token-only
  lookup here, so normal RLS applies without conflict).
- Full lifecycle: create → `OPEN`, `start` → `IN_PROGRESS`, `resolve` →
  `RESOLVED`, `close` → `CLOSED` (from any non-`CLOSED` state, not only from
  `RESOLVED` — closing a duplicate/invalid ticket shouldn't require
  resolving it first).
- `type`/`priority` enums lifted verbatim from CPMS
  (`TECHNICAL`/`BILLING`/`ACCOUNT`/`GENERAL`/`OTHER`,
  `LOW`/`MEDIUM`/`HIGH`/`URGENT`) — no reason to rename or reshape fields
  CPMS already validated.
- `create`/`listMine` require an authenticated principal but **no**
  `adm:tickets:*` permission — filing or checking on your own ticket is a
  self-service action, not an admin action.
- `listAll`/`start`/`resolve`/`close` require `adm:tickets:manage`.
- Optional `TicketEventPublisher.onCreated(...)` port, no-op default —
  lets a consumer forward the ticket into whatever real support tool they
  actually use (Zendesk, Jira, etc.), mirroring invitations' "no mail
  dependency" move: Gen_ADM never owned SUP-SVC's job and shouldn't invent
  a replacement for it.
- One new permission code (`adm:tickets:manage`), wired into
  `gen-adm-demo` + smoke test + docs.

**Out of scope (explicitly deferred):**
- Any actual support-desk functionality — comments/replies, attachments,
  SLAs, agent assignment, routing. Gen_ADM logs the request and its
  lifecycle state; a real support tool (via the event-publisher hook) is
  responsible for everything past that, exactly as SUP-SVC was in CPMS.
- No separate audit-log table — `startedAt`, `resolvedAt`/
  `resolvedByUserId`, `closedAt`/`closedByUserId` on the ticket row itself
  are the audit trail, matching the precedent set by every prior feature
  this session.
- No cross-tenant superadmin/support-staff concept — `adm:tickets:manage`
  is an ordinary tenant-scoped permission like every other Gen_ADM
  permission; there's no notion of a platform-wide support team spanning
  tenants (same boundary already established for impersonation).

## Architecture

### Entity & state machine

New table `support_tickets`:

```sql
CREATE TABLE support_tickets (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    requested_by_user_id  UUID NOT NULL,
    type                  VARCHAR(20) NOT NULL,
    subject               VARCHAR(255) NOT NULL,
    description           VARCHAR(5000) NOT NULL,
    priority              VARCHAR(20) NOT NULL,
    status                VARCHAR(20) NOT NULL,
    started_at            TIMESTAMPTZ,
    resolved_at           TIMESTAMPTZ,
    resolved_by_user_id   UUID,
    closed_at             TIMESTAMPTZ,
    closed_by_user_id     UUID,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_support_tickets_tenant_status
    ON support_tickets(tenant_id, status);

CREATE INDEX idx_support_tickets_tenant_requested_by
    ON support_tickets(tenant_id, requested_by_user_id);
```

RLS enabled/forced with the same `tenant_id = current_setting('app.tenant_id', true)::uuid`
policy used by every table except `invitations` (which has a documented,
deliberate exception — see
[[2026-07-24-gen-adm-invitations-design]]). Support tickets have no
token-only/no-tenant-known lookup path, so the normal policy applies
without conflict.

States:

```
OPEN --start--> IN_PROGRESS --resolve--> RESOLVED --close--> CLOSED
OPEN --close--------------------------------------------------^
IN_PROGRESS --close--------------------------------------------^
```

`close` is valid from `OPEN`, `IN_PROGRESS`, or `RESOLVED` — any
non-`CLOSED` state. `start`/`resolve` each require the ticket to be in
exactly the state immediately before them (`OPEN`/`IN_PROGRESS`
respectively) — 409 otherwise.

### Permissions & service API

One permission code: `adm:tickets:manage` — gates `listAll`, `start`,
`resolve`, `close`. `create` and `listMine` require only an authenticated
`GenAdmPrincipal`, no permission check — every tenant member can file a
ticket and check on their own.

`SupportTicketService`:

```java
SupportTicketEntity create(UUID tenantId, UUID requestedByUserId, SupportTicketType type,
        String subject, String description, SupportTicketPriority priority);
// status = OPEN

List<SupportTicketEntity> listMine(UUID tenantId, UUID requestedByUserId);
// tenant- and requester-scoped

List<SupportTicketEntity> listAll(UUID tenantId);
// tenant-scoped only, every status

SupportTicketEntity start(UUID tenantId, UUID ticketId);
// 404 if not found in tenant; 409 if not OPEN

SupportTicketEntity resolve(UUID tenantId, UUID ticketId, UUID resolvedByUserId);
// 404 if not found in tenant; 409 if not IN_PROGRESS

SupportTicketEntity close(UUID tenantId, UUID ticketId, UUID closedByUserId);
// 404 if not found in tenant; 409 if already CLOSED
```

Cross-tenant ticket lookups surface as 404, never 403 — same
anti-enumeration convention as every prior feature.

### REST controller + DTOs

`SupportTicketController` at `/api/v1/support-tickets`:

| Method | Path | Permission |
|---|---|---|
| POST | `/` | none — any authenticated principal |
| GET | `/mine` | none — any authenticated principal |
| GET | `/` | `adm:tickets:manage` |
| POST | `/{id}/start` | `adm:tickets:manage` |
| POST | `/{id}/resolve` | `adm:tickets:manage` |
| POST | `/{id}/close` | `adm:tickets:manage` |

`CreateSupportTicketRequest`: `type` (`@NotNull SupportTicketType`),
`subject` (`@NotBlank @Size(max=255)`), `description` (`@NotBlank
@Size(max=5000)`), `priority` (`@NotNull SupportTicketPriority`) — field
shapes and validation lifted verbatim from CPMS's own
`CreateSupportTicketRequest`.

`SupportTicketResponse`: `id`, `tenantId`, `requestedByUserId`, `type`,
`subject`, `description`, `priority`, `status`, `startedAt`, `resolvedAt`,
`resolvedByUserId`, `closedAt`, `closedByUserId`, `createdAt`.

### Event publisher port

Mirrors the established pattern (`OffboardingEventPublisher`,
`ImpersonationEventPublisher`, `InvitationEventPublisher`):

```java
public interface TicketEventPublisher {
    void onCreated(UUID tenantId, UUID ticketId, SupportTicketType type, SupportTicketPriority priority);
}
```

Default `NoOpTicketEventPublisher`. Synchronous hook only — no outbox, no
retry. A consumer registers its own bean to actually forward the ticket
into a real support tool.

## Testing

- `SupportTicketServiceImplTest` — unit test per transition (create,
  listMine/listAll scoping, start/resolve/close success paths, each
  wrong-state 409, cross-tenant 404).
- `SupportTicketControllerTest` — permission gating per endpoint,
  including that `create`/`listMine` require no permission check (only a
  principal).
- `SupportTicketIntegrationTest` — real persistence, full
  create→start→resolve→close flow, a close-from-OPEN flow (skipping
  IN_PROGRESS/RESOLVED), and cross-tenant 404.

## Wiring (gen-adm-demo, docs)

Matches the shape used for every prior feature:
- Migrations `V8__create_support_tickets_table.sql`,
  `V9__enable_support_tickets_rls.sql` (next free versions after
  invitations' `V7`, which itself has no RLS migration).
- Add `adm:tickets:manage` to `gen-adm-demo`'s permission catalog and
  `scripts/smoke-test.sh` (create a ticket as an ordinary user, list-mine,
  grant `adm:tickets:manage` to the viewer role, list-all, then
  start→resolve→close).
- `README.md` + `docs/integration-guide.md` sections describing the new
  endpoints, same structure as the existing sections.
