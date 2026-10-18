# audit-spring-boot-starter

Wires `audit-core`'s ports to real infrastructure — Postgres (the tamper-evident ledger), Mongo
(the rich event store), Rabbit (event ingestion, with cross-instance partition sharding),
scheduled chain verification, scheduled anchor publishing to S3 Object Lock, metrics, health, and
cross-instance distributed ordering — behind Spring Boot auto-configuration.

Published independently from `audit-core` (see [VERSIONING.md](../VERSIONING.md)); every
`audit.*` property this module binds, its default, and which auto-configuration class gates it
is listed in [PROPERTIES.md](PROPERTIES.md).

## What's here

- **JPA** (`persistence.jpa`, `AuditJpaAutoConfiguration`) — `ChainRepository` and
  `PartitionRegistry` backed by Postgres, with Flyway migrations at
  `classpath:db/migration/audit`.
- **Mongo** (`persistence.mongo`, `AuditMongoAutoConfiguration`) — the `EventStore` port,
  storing the full event payload that the Postgres ledger deliberately never does. This is the
  one place in the whole stack where `AuditEvent.payload` is actually persisted — see the root
  README's ["payload confidentiality is not this library's job"](../README.md#a-written-decision-payload-confidentiality-is-not-this-librarys-job)
  section for the explicit (not merely implicit) decision that encryption-at-rest for that
  payload is this Mongo deployment's responsibility, not this starter's.
- **Ingestion** (`ingestion`, `AuditIngestionAutoConfiguration`) — `AuditRecorder`, the
  recommended application-facing entry point composing `PartitionRegistry` + `AuditAppender` +
  `EventStore` into one call.
- **Messaging** (`wire`, `messaging`, `AuditRabbitAutoConfiguration`) — a Rabbit consumer mapping
  an external wire envelope onto `AuditRecorder`, filtered through partition-ownership sharding —
  see "Sharding" below.
- **Sharding** (`sharding`, `AuditRabbitAutoConfiguration`) — `PartitionShardResolver` answers
  "does this instance own this partition," consulted by `RabbitEventConsumer` before ever calling
  `AuditRecorder.record(...)` for a partition it doesn't own. Defaults
  (`audit.rabbit.shard.instance-index=0`, `audit.rabbit.shard.total-instances=1`) are a no-op for
  every single-instance deployment. A partition this instance doesn't own is republished with a
  self-managed retry counter, then redirected to a dead-letter queue (`<queue>.dead-letter`,
  auto-declared) once that counter exceeds 5 — bounding what would otherwise be a genuine infinite
  reject loop on a shared queue with competing consumers. **This proves ownership filtering
  works — it is not the final production distribution strategy**; real deployments typically
  solve steady-state distribution differently (a queue-per-shard topology, or RabbitMQ's own
  consistent-hash-exchange plugin), correctly out of scope for this library. See
  `RabbitEventConsumer`'s Javadoc for the full reasoning, including why this uses a custom header
  instead of RabbitMQ's native `x-death` tracking.
- **Verification** (`partition`, `verification`, `AuditVerificationAutoConfiguration`) —
  scheduled, daily, full-catalog chain verification. See below.
- **Anchoring** (`anchor`, `scheduling`, `AuditAnchorAutoConfiguration`) — daily
  `AnchorPublisherJob` publishes an append-only, S3-Object-Lock-protected anchor (partition key,
  chain tip hash, timestamp) for every known partition, via the `ObjectLockPort` driven port
  `audit-core` exposes. Inert until `audit.anchor.bucket` is explicitly configured — see
  `AuditAnchorAutoConfiguration`'s Javadoc for why this starter deliberately does not activate
  merely because JPA/S3-SDK are present. A single partition's publish failure is recorded and
  reported via `AnchorPublishFailedEvent`, not allowed to abort the run for any other partition.
- **Metrics** (`metrics`, `AuditMetricsAutoConfiguration`) — one `AuditMetricsRecorder` interface
  behind message-consumption, ledger-append, verification, and anchoring outcomes alike,
  Micrometer-backed when present, a no-op otherwise. `MicrometerAuditMetricsRecorder` is the only
  class in this starter permitted to name `MeterRegistry` — see the "Dependency pattern" section
  below for why.
- **Health** (`health`, `AuditHealthAutoConfiguration`) — `AuditChainHealthIndicator` reports
  `UP`/`DOWN` from the last completed verification run's clean/broken partition counts (and the
  last anchoring run's timestamp, if anchoring is configured), `UNKNOWN` before any run has
  completed. It only ever reads each job's cached last-run summary — it never triggers
  verification or anchoring itself, so a load balancer polling `/actuator/health` every few
  seconds never causes a real chain walk or S3 call.
- **Config validation** (`config`, `AuditProperties`) — `@Validated` plus a custom
  `NotBlankIfPresent` constraint on `audit.anchor.bucket`: `null` (never configured) is valid,
  since most applications never enable anchoring at all, but an explicitly blank value fails
  fast at startup with a clear message instead of surfacing later as an AWS region-resolution
  error inside `S3ObjectLockAdapter`. Only takes effect if a JSR-380 validator implementation
  (`spring-boot-starter-validation`) is on the *consuming* application's own classpath — see
  `AuditProperties`'s Javadoc.

## Scheduled chain verification

`ChainVerifierJob` runs on a cron schedule (`audit.verification.cron`, default daily at 02:00)
and iterates every partition returned by `PartitionCatalog.listAll()`, calling `AuditVerifier`
on each one. Any partition that doesn't verify as `OK` gets an `AuditChainBreakDetectedEvent`
published via Spring's own `ApplicationEventPublisher` — no bespoke listener port was invented
for this, since `@EventListener` already solves "notify interested parties without the
publisher hardcoding what happens downstream." The starter's default reaction is
`ChainBreakLoggingListener`, which logs at `ERROR` (a chain break always pages in a real
deployment); a consuming application can supply its own bean of that same type to replace the
default behavior entirely, or add an independent `@EventListener` alongside it with zero changes
to this starter.

**Verification runs sequentially, partition by partition, by design — not because
parallelizing it was overlooked.** Before changing this, think through: ordering guarantees (or
lack thereof) across partitions, the concurrent load N-way parallel full-chain scans would put on
the underlying database, and the operational blast radius of that load landing on a production
instance during business hours rather than a quiet 2am window. If verification throughput ever
genuinely needs to improve, that should be a deliberate, separately-reviewed decision made with
those trade-offs explicitly in mind — not a default reached for casually because a loop looked
parallelizable.

`PartitionCatalog` (enumeration) is a deliberately separate interface from `PartitionRegistry`
(lifecycle — may write a row on first call for a new key). `ChainVerifierJob` depends only on
the former: a read-only verification path has no business invoking a write-capable lifecycle
operation just to iterate partitions that already exist.

## Scheduled anchor publishing

`AnchorPublisherJob` runs on its own cron schedule (`audit.anchor.publish-cron`, default daily at
03:00 — an hour after verification), reusing the same `PartitionCatalog` rather than introducing
a second enumeration mechanism. For each partition, `AnchorPublisher` reads the chain's current
tip via `ChainRepository.findTip` and calls `ObjectLockPort.publishAnchor` — `audit-core`'s
driven port, implemented here by `S3ObjectLockAdapter`, which writes one small JSON object per
call, keyed by both partition and publish timestamp so a run never overwrites a prior anchor, with
S3 Object Lock Compliance-mode retention (`audit.anchor.retention-years`, default 7 years).

**Runs sequentially, partition by partition, by design — same reasoning as scheduled chain
verification above.** A single partition's publish failure is caught, recorded in the returned
`AnchorSummary`, and published as an `AnchorPublishFailedEvent` — it does not stop the run for any
other partition. The default reaction, `AnchorPublishFailureLoggingListener`, logs at `ERROR` and
is replaceable the same way `ChainBreakLoggingListener` is.

**On LocalStack, honestly scoped: this starter's own tests against a Testcontainers LocalStack S3
endpoint prove that `S3ObjectLockAdapter` makes the correct API calls with the correct retention
configuration — they do not, and cannot, prove that S3 Object Lock actually rejects a delete or
overwrite attempt the way real AWS S3 Compliance mode does.** LocalStack accepts and stores the
retention configuration without enforcing it. That stronger guarantee has never been verified
against a real AWS account by this project — do that once before relying on it in production.

## Cross-instance distributed ordering

`JpaChainRepository.append` coordinates across genuinely independent instances (each its own
JVM, own connection pool) via a Postgres advisory lock
(`pg_advisory_xact_lock(hashtext(partitionKey))`) — the same database every instance already
writes to, not a new coordination system. The lock alone only narrows the race window it doesn't
close it: while holding it, `append` re-reads the partition's actual current tip and compares it
against what the caller's `ChainedRecord` was computed from, rejecting a stale caller via
`SeqConflictException` *before* any insert is attempted.

`audit.jpa.partition-lock-timeout` (default 5s) bounds both how long a caller waits to acquire the
lock and the transaction's `statement_timeout`. `audit.jpa.append-max-attempts` (default 20, not
`audit-core`'s own bare default of 3) exists because of a measured, not assumed, structural
consequence: under N-way synchronized contention, only one contender drains per retry round, so
the unluckiest of N contenders can need up to N attempts. A follow-up jittered backoff fix in
`DefaultAuditAppender` (internal-only, `audit-core`) measurably cut the retry-exhaustion rate this
mechanism produces under 100-way contention from ~69% to ~5–9% — see
[`docs/adr/0001-distributed-ordering-advisory-locks.md`](../docs/adr/0001-distributed-ordering-advisory-locks.md)
for the full reasoning, the measured numbers, and why the two-call `findTip()`/`append()`
protocol itself is a recorded, deliberate tradeoff rather than a structural fix.

## Dependency pattern

Every optional infrastructure dependency (JPA, Mongo, Rabbit, S3, Micrometer, Actuator, JSR-380
validation) follows the same shape: the starter declares it `compileOnly`/`testImplementation`, a
consuming application brings the real dependency itself, and the relevant `@AutoConfiguration`
class is gated with `@ConditionalOnClass`/`@ConditionalOnBean` (and, for anchoring, an additional
`@ConditionalOnProperty`) so the starter degrades gracefully.

For Micrometer specifically, that degradation goes one step further than a conditional bean:
`AuditMetricsRecorder` (see "Metrics" above) is the single interface every metrics-emitting call
site depends on, and `MicrometerAuditMetricsRecorder` is the *only* class in this codebase
permitted to name `MeterRegistry` at all, confined to one `@ConditionalOnClass`-gated nested
configuration class. This isn't stylistic — a field of that type anywhere else, even one that's
always `null` when Micrometer is absent, would fail that class's *loading* via reflection the
moment anything calls `getDeclaredFields()`/`getDeclaredMethods()` on it (any unrelated
`BeanPostProcessor` can trigger this), regardless of what `@Conditional` annotation would
otherwise have prevented the bean from ever being created — found via an actual
`NoClassDefFoundError` running `audit-demo` without Micrometer on its classpath, not assumed. See
`AuditMetricsRecorder`'s Javadoc for the full story, and `AuditRabbitAutoConfiguration`'s Javadoc
for where this was originally discovered.
