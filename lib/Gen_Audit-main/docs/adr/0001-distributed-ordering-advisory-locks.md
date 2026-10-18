# ADR 0001: Distributed ordering via Postgres advisory locks

**Status:** Accepted
**Phase:** 8

## Update (Phase 9): relationship to partition-ownership sharding

This ADR predates Phase 9's `sharding.PartitionShardResolver`, which filters inbound Rabbit
messages by partition ownership *before* `AuditRecorder.record(...)` — and therefore before any
call ever reaches `JpaChainRepository.append()` — so that in a correctly configured multi-instance
deployment, only one instance's consumer ever routes messages for a given partition at all. That
reduces how often the specific cross-instance race this ADR addresses actually manifests in
practice: with sharding active and topology configuration consistent across instances, two
different instances contending for the same partition's advisory lock should be rare rather than
routine.

**It does not remove the need for the advisory lock or its freshness check.** Sharding is
ownership *filtering* at the message-consumption boundary, not a correctness *guarantee* at the
persistence boundary — it has no way to prevent a misconfigured topology (e.g. two instances
sharing the same `instance-index`), a mid-rebalance window where two instances briefly both believe
they own a partition, or a caller that reaches `JpaChainRepository` by some path other than the
sharded Rabbit consumer. The advisory lock plus in-lock freshness check (and, beneath both, the
`UNIQUE` constraint) remains the actual correctness guarantee for exactly those cases; sharding is
an optimization that reduces contention frequency, layered on top, the same way this ADR already
describes the advisory lock itself as layered on top of the `UNIQUE`-constraint-plus-retry
mechanism rather than replacing it.

## Context

Real `AUDIT-SVC` deployments run multiple instances per region, each its own JVM. Phase 2 closed
a single-JVM race (many threads racing to append to the same partition) with an in-process
`ConcurrentHashMap<String, ReentrantLock>` inside `JpaChainRepository` — but that lock exists only
within one JVM's heap. Two independent instances can each read the same partition tip and race to
append, with nothing today stopping it. This ADR covers the mechanism Phase 8 introduced to close
that gap, and the tradeoffs deliberately accepted (or deferred) along the way.

## Decision: Postgres advisory locks, not a new coordination system

Every instance already shares one Postgres — the same database `JpaChainRepository` already
writes to. `pg_advisory_xact_lock(hashtext(partitionKey))` gives cross-instance mutual exclusion
for free, scoped to the existing `@Transactional` boundary, with zero new infrastructure.

We deliberately did not introduce Redis, ZooKeeper, or etcd for this. Doing so would mean
operating, monitoring, and reasoning about a second coordination system for a problem the first
one — the database every instance must already reach to do anything at all — already solves. That
would be pure added complexity with no corresponding correctness benefit: nothing about
distributed lock semantics requires the lock and the data it protects to live in different
systems, and keeping them in the same system removes an entire class of "the lock said yes but the
data says no" partial-failure modes that cross-system coordination would introduce.

### `pg_advisory_xact_lock`, not `pg_advisory_lock`

The transaction-scoped form releases automatically at commit or rollback, tied to the existing
`@Transactional` boundary around `JpaChainRepository.append`. The session-scoped form
(`pg_advisory_lock`/`pg_advisory_unlock`) requires an explicit, manual unlock — under connection
pooling, a crashed or misbehaving caller could hold that lock forever, poisoning the partition for
every future writer until an operator intervenes. The transaction-scoped form has no equivalent
failure mode: a crashed connection's transaction rolls back and the lock releases, full stop.

## Decision: this is additional pessimistic coordination, layered on the existing optimistic one — not a replacement for it

Phase 1's `UNIQUE(partition_key, seq)` constraint plus `DefaultAuditAppender`'s bounded retry is
optimistic concurrency control: attempt the write, let the database reject a conflict, retry. That
mechanism is still the actual correctness guarantee after this phase — it is what makes a
duplicate `seq` structurally impossible to persist, full stop, regardless of anything the
application layer does or fails to do correctly.

The advisory lock does not replace that. It exists to reduce how *often* the optimistic path's
retry loop needs to fire — the retry-storm Phase 2's load test first exposed (many callers reading
the same stale tip, racing, and only one winning per round) — by serializing the read-decide-write
critical section per partition. If the advisory lock were somehow bypassed entirely (a bug, a
future adapter that forgets to acquire it), the `UNIQUE` constraint still prevents corruption; it
would just mean the retry storm returns. This layering is deliberate: correctness must never
depend on the coordination layer working correctly, only on the constraint.

### Why not `SERIALIZABLE` isolation instead?

`SERIALIZABLE` transaction isolation was considered as an alternative to an explicit lock.
Rejected for two reasons:

1. **Wrong scope.** `SERIALIZABLE` detects conflicts across the *entire* transaction — every table
   and row it touches — not narrowly the one partition key a given `append()` call actually cares
   about. Two transactions touching entirely unrelated partitions could still abort each other if
   they happen to touch some other unrelated overlapping data, whereas the advisory lock's
   critical section is exactly, and only, the partition key involved.
2. **Wrong failure mode.** `SERIALIZABLE`'s conflict resolution is abort-and-retry-from-scratch —
   the *entire* transaction rolls back and must be reissued from the application, with no
   ordering guarantee about which of two conflicting transactions gets to proceed next. An
   explicit lock instead makes losers *wait in line* for the winner in roughly arrival order —
   more predictable, and very likely far less retry-heavy under the sustained contention this
   phase's own load test exercised, since a waiting transaction that eventually acquires the lock
   only needs its own freshness re-check to pass, not a full application-level retry cycle.

## Recorded tradeoff: the two-call protocol stays as-is this phase

`audit-core`'s `ChainRepository` port (frozen) exposes `findTip()` and `append()` as two separate
calls; `DefaultAuditAppender` (also frozen) calls them uncoordinated, once per attempt. A cleaner
long-term design would have the repository own the *entire* operation — `append(event)` internally
acquiring the lock, reading the true latest tip, computing the next record, and persisting, all as
one atomic unit — removing the stale-read problem structurally instead of detecting it after the
fact. That would require changing `ChainRepository`'s method signature, a breaking change to a
frozen, versioned port.

This phase deliberately does not make that change. Per this project's evidence-based discipline
(a core-module change requires actual evidence it's needed, not speculation), the two-call
protocol's cost is now *measured*, not merely theorized: see "Measured evidence" below. The
takeaway recorded here for whoever eventually does the major-version redesign: the fix that
actually removes retry-storm-under-contention as a phenomenon is restructuring `ChainRepository`
to own read-decide-write atomically, not adding more coordination to the outside of it.

### Structural consequence: retries scale with contenders, not a constant

Because the advisory lock only wraps `append()` (never `findTip()`, which `DefaultAuditAppender`
calls unlocked, before the lock exists), many concurrent callers can still read the *same* stale
tip before any of them acquires the lock. When they do, the lock correctly serializes their
attempts to actually write — but because they all computed the *same* candidate `seq` from the
same stale read, exactly one of them succeeds per "round"; the rest are rejected by the in-lock
freshness check and must retry with a fresh read. Under N-way simultaneous, tightly synchronized
contention on one partition, the single unluckiest caller can need up to N attempts, not a small
constant — this is a structural property of "detect staleness after the fact," not a bug in this
implementation.

## Recorded evolution path: `hashtext()`'s 32-bit collision space

`hashtext()` returns a 32-bit hash; `pg_advisory_xact_lock`'s single-argument form takes a 64-bit
key (the 32-bit value is implicitly widened). Two unrelated partition keys can theoretically
collide in that 32-bit space, causing unrelated tenants to serialize against each other
unnecessarily. This is an accepted, deliberate liveness tradeoff — occasional unnecessary
serialization between two tenants who happen to collide, never incorrect data (the freshness check
and the `UNIQUE` constraint both operate on the real `partition_key` column, not the hash) — not
implemented around this phase because no evidence of a real collision rate exists yet.

If collision rates ever become measurable at production scale, the migration path is
`pg_advisory_xact_lock(int, int)` — the two-`int` overload — fed by a stable 64-bit hash computed
from `partitionKey` (e.g. splitting a 64-bit hash, such as a truncated SHA-256 or a proper 64-bit
hash function, into two 32-bit halves). No code change was made for this now; this paragraph
exists so the fix isn't rediscovered under production pressure later.

## Measured evidence: why `audit.jpa.append-max-attempts` defaults to 20, not 3

`audit-core`'s own `DefaultAuditAppender` default (3 attempts) predates this phase and is
unchanged as a *default* — callers of `AuditAppender.create(repository, clock)` still get 3. That
default was proven insufficient for this starter's own regression test the moment the advisory
lock replaced the Phase 2 in-JVM lock:

- **Phase 2's 20-thread single-JVM concurrency test**, re-run against the pure Postgres-advisory-lock
  mechanism with the unchanged 3-attempt default, failed with **17 of 20 threads exhausting their
  retries** — reproducing, under a different mechanism, the exact failure this project saw once
  before (see `JpaChainRepository`'s Phase 2 Javadoc history). The round-based analysis above
  predicts this precisely: with 20 fully synchronized contenders and only one winner draining per
  round, the unluckiest contender needs up to 20 attempts; 3 was never going to be enough.
- Raising the starter's configured value to **20** made that same test pass reliably across
  repeated runs (confirmed, not assumed).
- **`PartitionLoadTest`** (100 concurrent appenders across 2 independent instances, 1000 total
  events against one partition) — a deliberately more extreme scenario than the 20-thread
  regression test — was then run with that same value, purely to observe real-world behavior, not
  to pass/fail against an invented threshold (see that test's own Javadoc for why). Measured
  across three runs: **~69% of individual append attempts exhausted all 20 retries** (roughly 309
  of 1000 events succeeded per run), with p50 append latency around 700–740ms and p99 around
  2.8–3.2s for attempts that did succeed.

This second number is reported honestly, not chased: raising `append-max-attempts` further would
only shift where the exhaustion rate lands for *this specific* contention level (100-way), not
fix the structural cause (see "Structural consequence" above) — a mathematically higher value
just moves the goalposts for whatever contention level is tested next. **20 remains the chosen
default because it is the smallest value that reliably clears this starter's own correctness
regression test (20-way single-JVM contention)**, which is the actual bar Phase 8 needs to clear;
the 100-way number is recorded here as real, useful signal about this design's behavior under
contention well beyond that bar — signal for a future capacity-planning or batching decision, not
evidence that this phase's chosen default is wrong for what it was chosen to guarantee.

**This phase proves distributed correctness only; it does not establish production throughput.**
Every measurement above is exactly that — a measurement, not a target. Message batching, partition-
to-instance assignment, and consumer concurrency tuning remain separate, later, application-level
concerns, to be taken up only if real production traffic patterns show they're actually needed.

## Follow-up: jittered backoff addresses the cause, not just the symptom

The 69% figure above has a specific, addressable cause: every rejected contender's retry loop
re-reads the tip and races again *immediately*, so contenders that collided in one round tend to
collide again in the next — the retry storm doesn't naturally dissipate on its own. `20` attempts
was always treating the *symptom* (give the unluckiest contender enough tries to eventually win a
round), not the *cause* (contenders keep recolliding instead of spreading out over time).

`DefaultAuditAppender` (still entirely `internal`, no public API or version-bump implication) now
sleeps for a small, randomized, exponentially-growing delay before each retry (not before the
first attempt) — full-jitter backoff, base 5ms, capped at 200ms. This desynchronizes contenders
that would otherwise retry in lock-step, so a given round is far less likely to have every
remaining contender collide on the exact same stale read again.

**Measured effect, same `PartitionLoadTest` scenario, `append-max-attempts` still 20:**
retry-exhaustion rate dropped from ~69% to **~5–9%** across repeated runs, and p50 append latency
for successful attempts dropped from ~700–740ms to **single-digit milliseconds** (contenders now
mostly succeed within their first couple of attempts instead of queuing through many rounds). p99
latency for the minority that still need several rounds remained comparable (~2.2–3.3s).

**Re-evaluated whether `append-max-attempts` could now come back down, per the same
evidence-over-assumption discipline used to raise it in the first place** — actually tried lower
values against the 20-thread regression test, not merely reasoned about it:

| `append-max-attempts` | 20-thread regression test | 100-way `PartitionLoadTest` exhaustion rate |
|---|---|---|
| 8 | Flaky — 1 failure in 10 runs | not measured further, already disqualified |
| 12 | Reliable — 10/10 runs | ~24–28% |
| 20 (kept) | Reliable — 10/10 runs | ~5–9% |

12 technically clears the correctness bar, but at a materially worse 100-way outcome, for no
corresponding latency or resource saving — jitter's own cost is small enough (a few hundred
milliseconds of worst-case added sleep across the whole retry loop) that spending down the retry
budget specifically to prove a lower number survives isn't a good trade. **20 stays the default**:
with jitter, it is now the smallest value that clears the correctness bar *with comfortable
margin*, not merely the smallest value that happens to pass once — and it now comes with a
materially better high-contention outcome as a side effect of jitter alone, not because the retry
budget itself grew.
