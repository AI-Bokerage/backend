# Gen_ADM Data Export Jobs — Design

## Why

`data-export jobs` was explicitly deferred in the RBAC design spec
([[2026-07-23-gen-adm-rbac-design]]) as CPMS-specific: in CPMS-Platform's
`adm-svc`, an export request is a pure proxy — `ExportServiceImpl` forwards
`request`/`status`/`list`/`cancel` straight to a separate TNT-SVC via
`TntSvcExportClient`
(`pre-context/adm-svc/src/main/java/com/example/admsvc/application/impl/ExportServiceImpl.java`).
TNT-SVC owns a much richer lifecycle than any prior ported feature:
`PENDING_APPROVAL → APPROVED/REJECTED → COMPLETED/FAILED`, gated by a
cross-tenant Super Admin approval step, with the finished export delivered
as a presigned S3 `downloadUrl`.

Gen_ADM has none of that infrastructure — no TNT-SVC, no blob storage, and
(per the boundary already established for impersonation) no cross-tenant
superadmin concept at all. Rather than reproduce CPMS's shape, this feature
is scoped to what Gen_ADM actually is: an RBAC library that owns a small,
well-known set of tenant-scoped tables. An "export" here is a snapshot of
everything Gen_ADM itself owns for one tenant — generated synchronously,
stored in the same database, and served back over an authenticated
endpoint.

## Scope

**In scope:**
- `DataExportEntity` — own table, normal tenant-scoped RLS (same pattern
  as offboarding/impersonation/support-tickets; no token-only lookup
  conflict, unlike invitations).
- Synchronous generation: `request` builds the full snapshot inline and
  returns `COMPLETED` immediately. No `PENDING_APPROVAL`/`APPROVED`/
  `REJECTED`/`FAILED` — those states existed in CPMS only to model a
  cross-tenant approval workflow Gen_ADM doesn't have.
- Snapshot content: everything Gen_ADM owns for the tenant — roles (with
  their permission codes), user↔role assignments, offboarding jobs,
  impersonation sessions, invitations, and support tickets. One JSON
  document per export.
- Storage: the snapshot JSON lives directly on the `DataExportEntity` row
  (a `TEXT` column, same convention as invitations' `roleIdsJson`). No
  blob storage, no storage abstraction — a consumer who later needs real
  S3-backed storage can fork this table, but nothing here should be built
  for that hypothetical today.
- Lifecycle: `COMPLETED → EXPIRED` (lazy, read-time, TTL-based — same
  pattern as impersonation/invitations) and `COMPLETED → REVOKED`
  (explicit action, replaces CPMS's "cancel a pending request" since
  there's no longer a pending state to cancel). Both are terminal; neither
  reverses.
- `gen-adm.export-ttl-days` config property, default 7 — same shape as
  `gen-adm.invitation-ttl-days`.
- One permission code, `adm:exports:manage`, gating every operation
  (request/list/status/download/revoke) — the snapshot contains the
  tenant's full RBAC configuration plus every other feature's lifecycle
  records, so unlike support tickets there is no self-service tier.
- Wired into `gen-adm-demo` + smoke test + docs, matching every prior
  feature.

**Out of scope (explicitly deferred):**
- Any approval workflow, cross-tenant or otherwise. If a future need
  arises for someone-other-than-the-requester to gate export creation,
  that's a new design question, not an assumption baked in now.
- Real external/blob storage, or a pluggable storage port. Everything
  Gen_ADM's own database can already hold; introducing a port for a
  hypothetical S3 integration nobody has asked for is exactly the kind of
  speculative abstraction this session's features have avoided elsewhere.
- An event-publisher port (unlike `OffboardingEventPublisher` →
  `ImpersonationEventPublisher` → `InvitationEventPublisher` →
  `TicketEventPublisher`). Every one of those existed to let a consumer
  forward work to a *real external system* Gen_ADM was deliberately not
  reimplementing (SMTP, a ticketing tool). Export generation has no such
  external counterpart to decouple from — it's Gen_ADM's own data,
  queried by Gen_ADM's own repositories, staying inside Gen_ADM's own
  database. A port here would have nothing to forward to.
- Filtering/scoping options on what an export contains (CPMS's `scope`
  field, e.g. `"TENANT_FULL"`). There is exactly one scope — everything
  Gen_ADM owns for the tenant — so a field that only ever holds one value
  is not worth adding.
- Pagination on `listExports` — no other Gen_ADM list endpoint paginates
  (`listActionable`, `listAll` for tickets, etc. all return a plain
  `List<>`); a tenant's export history is not expected to be large enough
  to need it.

## Architecture

### Entity & state machine

New table `data_exports`:

```sql
CREATE TABLE data_exports (
    id                    UUID PRIMARY KEY,
    tenant_id             UUID NOT NULL,
    requested_by_user_id  UUID NOT NULL,
    status                VARCHAR(20) NOT NULL,
    snapshot_json         TEXT,
    record_count          BIGINT NOT NULL,
    expires_at            TIMESTAMPTZ NOT NULL,
    revoked_at            TIMESTAMPTZ,
    revoked_by_user_id    UUID,
    version               BIGINT,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_data_exports_tenant_status
    ON data_exports(tenant_id, status);
```

RLS enabled/forced with the standard
`tenant_id = current_setting('app.tenant_id', true)::uuid` policy — the
same policy used by every table except `invitations` (documented exception,
see [[2026-07-24-gen-adm-invitations-design]]). Migrations
`V10__create_data_exports_table.sql` / `V11__enable_data_exports_rls.sql`
(next free versions after support tickets' `V8`/`V9`).

`snapshot_json` is nullable: it holds the export content only while
`status = COMPLETED`. The moment an export transitions to `EXPIRED` or
`REVOKED`, the service clears it — there is no reason to keep a sensitive
tenant-wide RBAC dump around past the point it's no longer downloadable.

States:

```
COMPLETED --(TTL elapses, read-time check)--> EXPIRED
COMPLETED --revoke------------------------------> REVOKED
```

Both `EXPIRED` and `REVOKED` are terminal. `download` and `revoke` both
require `status = COMPLETED`; anything else is a 409.

### Snapshot content

`request` synchronously queries Gen_ADM's own repositories for the tenant
and serializes the result into one JSON document:

```json
{
  "tenantId": "...",
  "generatedAt": "...",
  "roles": [ { "id": "...", "name": "...", "description": "...", "systemRole": false, "permissionCodes": ["..."] } ],
  "userRoleAssignments": [ { "id": "...", "userId": "...", "roleId": "...", "assignedAt": "..." } ],
  "offboardingJobs": [ ... ],
  "impersonationSessions": [ ... ],
  "invitations": [ ... ],
  "supportTickets": [ ... ]
}
```

Each section is a plain projection of the corresponding entity's fields
already used by that feature's own `Response` DTO — no new shape invented,
just assembled into one document. `record_count` on the entity is the sum
of all section lengths, so a caller can gauge the export's size from
`getStatus`/`listExports` without downloading it.

Four existing repositories need one additive tenant-wide query method each
(none of these change existing method behavior):
- `UserRoleAssignmentRepository.findAllByTenantId(UUID): List<...>`
- `OffboardingJobRepository.findAllByTenantId(UUID): List<...>`
- `ImpersonationSessionRepository.findAllByTenantId(UUID): List<...>`
- `InvitationRepository.findAllByTenantId(UUID): List<...>`

(`RoleRepository.findAllByTenantId` and `SupportTicketRepository.findAllByTenantId`
already exist and are reused as-is.) `PermissionEntity` is a global,
non-tenant-scoped catalog referenced by `RoleEntity.permissions` — the
snapshot includes each role's permission *codes* inline via that
relationship, so no separate global-catalog dump is needed.

### Permissions & service API

One permission code: `adm:exports:manage` — gates every operation. Unlike
support tickets, there is no no-permission-required self-service tier: an
export contains the tenant's entire RBAC configuration and every other
feature's lifecycle records, not just the caller's own data.

`DataExportService`:

```java
DataExportEntity request(UUID tenantId, UUID requestedByUserId);
// synchronously builds the snapshot, status = COMPLETED, expiresAt = now + export-ttl-days

DataExportEntity getStatus(UUID tenantId, UUID exportId);
// lazy-expires if overdue, then returns; 404 if not found in tenant

List<DataExportEntity> listExports(UUID tenantId);
// tenant-scoped, every status; each entry lazy-expired if overdue before being returned

String download(UUID tenantId, UUID exportId);
// lazy-expires if overdue; returns snapshotJson; 409 if status != COMPLETED

DataExportEntity revoke(UUID tenantId, UUID exportId, UUID revokedByUserId);
// lazy-expires if overdue; 409 if status != COMPLETED; else sets REVOKED, clears snapshotJson
```

Cross-tenant lookups surface as 404, never 403 — same anti-enumeration
convention as every prior feature.

### REST controller + DTOs

`DataExportController` at `/api/v1/exports`:

| Method | Path | Permission |
|---|---|---|
| POST | `/` | `adm:exports:manage` |
| GET | `/{id}` | `adm:exports:manage` |
| GET | `/` | `adm:exports:manage` |
| GET | `/{id}/download` | `adm:exports:manage` |
| POST | `/{id}/revoke` | `adm:exports:manage` |

`POST /` takes no request body — there is exactly one export scope, so
nothing to parameterize. Returns `200 OK` (not `202 Accepted` — generation
has already finished by the time the response is written).

`DataExportResponse`: `id`, `tenantId`, `requestedByUserId`, `status`,
`recordCount`, `expiresAt`, `revokedAt`, `revokedByUserId`, `createdAt` —
used by `request`/`getStatus`/`listExports`. Deliberately excludes
`snapshotJson` — the status/list views answer "does this export exist and
is it still good," not "here is the data," keeping large payloads off
those responses.

`GET /{id}/download` returns the raw stored JSON string directly
(`Content-Type: application/json`), read straight from `snapshotJson` — no
separate download DTO.

## Testing

- `DataExportServiceImplTest` — unit test per transition: `request`
  assembles all six sections correctly and sums `recordCount`; `download`
  succeeds while `COMPLETED` and 409s once `EXPIRED`/`REVOKED`; `revoke`
  succeeds from `COMPLETED` and 409s otherwise; lazy expiry flips status
  and clears `snapshotJson` on a read past `expiresAt`; cross-tenant 404.
- `DataExportControllerTest` — permission gating on all five endpoints
  (all require `adm:exports:manage`, no self-service tier to verify this
  time).
- `DataExportIntegrationTest` — real persistence, full
  request→download→revoke flow, and a request→(TTL elapses)→lazy-expiry→
  download-409 flow.

## Wiring (gen-adm-demo, docs)

Matches the shape used for every prior feature:
- Migrations `V10__create_data_exports_table.sql`,
  `V11__enable_data_exports_rls.sql` (next free versions after support
  tickets' `V8`/`V9`).
- Add `adm:exports:manage` to `gen-adm-demo`'s permission catalog and
  `scripts/smoke-test.sh` (request an export, list, get status, download,
  revoke, confirm a second download attempt now 409s).
- `README.md` + `docs/integration-guide.md` sections describing the new
  endpoints, same structure as the existing sections.
