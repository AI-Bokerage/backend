# ADR 0002: AUD-SVC's persistence model

**Status:** Recommendation — validate against real `AUD-SVC` requirements before treating as final.

## Context

This has been an open question since before any code in this repository existed. The source LLD's
§11.8/§11.8.4 conflicted with itself on `AUD-SVC` (platform audit — Support Admin actions, billing
events): one section implies platform audit is Mongo-only, another implies read-only views over the
same `audit_immutable`-style hash chain `AUDIT-SVC` uses. Building `AUD-SVC` without resolving this
first would mean carrying an architectural ambiguity into implementation, discovering the conflict
mid-build, and likely redoing work either way.

Every claim below is phrased as a recommendation — "the recommended initial implementation is..." —
not a mandate. This is deliberate: real `AUD-SVC` requirements don't exist yet, and this
recommendation should be revisited against them, not treated as settled simply because it's written
down.

## Recommendation: reuse the `audit-core`/`audit-spring-boot-starter` stack, unmodified

**The recommended initial implementation gives `AUD-SVC` the same hash-chain treatment as
`AUDIT-SVC`, using the exact same library stack.** Two reasons converge on this:

1. **The source spec's own `PlatformAuditEvent.chainHash` field.** If platform audit events are
   expected to carry a chain hash at all, the spec's own data model already assumes tamper-evidence
   equivalent to what `audit-core` provides. Building a second, different tamper-evidence mechanism
   just for platform events — while a chain-hash field sits unused or reinvented — would contradict
   the spec's own modeling.
2. **Building a second mechanism wastes the entire reason this library exists.** `audit-core` and
   the starter exist specifically so that "does this event's history verifiably resolve to what
   actually happened" doesn't need re-solving per service. A lighter, different mechanism for
   `AUD-SVC` would mean re-deriving (and re-validating, and re-documenting) most of what Phases 1–8
   already built, tested, and hardened, for no benefit tied to anything platform-audit-specific.

Concretely: `AUD-SVC` depends on `audit-spring-boot-starter` the same way `AUDIT-SVC` will, wires
its own `ChainRepository`/`EventStore` backing (or reuses the same Postgres/Mongo topology, if
deployment-appropriate), and gets scheduled verification, anchoring, metrics, health, and — now —
sharding for free, with zero library changes.

## Recommendation: platform audit is not tenant-partitioned

The remaining open question was what `AUD-SVC` uses as `partitionKey`. **The recommended initial
implementation is a single global partition** (e.g. `"platform"`, or one per region if `AUD-SVC` is
ever deployed multi-region) — not one partition per tenant.

**Reasoning:** `PlatformAuditEvent.tenantId` is nullable in the source spec, and it is a
*reference* — which tenant a Support Admin action happened to affect — not a partitioning
dimension for the chain itself. A super-admin suspending one tenant and then refunding a different
tenant a moment later are both platform-level administrative actions in one continuous
administrative audit trail; they are not naturally two different chains just because they happened
to touch different tenants. Tenant-partitioning `AUD-SVC`'s chain the way `AUDIT-SVC` partitions
tenant audit data would be importing a partitioning scheme that fits `AUDIT-SVC`'s actual access
pattern (per-tenant audit history) onto data whose natural access pattern is different (the
platform's own continuous administrative history, only sometimes filtered by which tenant an
action happened to touch).

**Where `tenantId` belongs instead:** carried in `AuditEvent.resourceType`/`resourceId` (or
folded into the event's `payload`, if richer structure is needed) — queryable in the rich Mongo
copy `EventStore` already persists, available to `AUD-SVC`'s own query/export API (not yet built —
see the Phase 9 phase summary) for "show me every platform action that touched tenant X," without
that filter ever needing to be a hash-chain partitioning key.

## What this recommendation does not decide

- Whether `AUD-SVC` needs its own Postgres/Mongo instances or shares infrastructure with
  `AUDIT-SVC` — a deployment/capacity decision, not an architectural one this ADR addresses.
- The real event catalog (`auth.login.success`, `task.created`, etc., per §14.12.23) — separate,
  substantial CPMS-side work, tracked in the Phase 9 phase summary's "what's left" list, not here.
- RBAC integration (`super.audit.read`/`audit.export`) — explicitly out of scope for this library by
  design since Phase 1; still needs building in `AUD-SVC` itself.

## Revisit this recommendation if

- Real `AUD-SVC` requirements surface a genuine need to scale writes across more than one
  concurrent writer for platform audit specifically (the single-global-partition recommendation
  trades that scaling dimension away deliberately, on the assumption platform-audit write volume
  is far below tenant-audit write volume — confirm this assumption against real numbers before
  relying on it).
- A future requirement emerges for platform audit to be genuinely partitionable in a way that
  doesn't fit "tenant reference, not partition key" (for example, per-region platform chains, if
  `AUD-SVC` becomes multi-region — already flagged above as a reasonable partition boundary if it
  becomes real).
