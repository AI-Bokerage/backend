# audit-java

A reusable, tamper-evident, hash-chained audit ledger. Start here if you're new to this
repository; each module also has its own README with implementation-level detail.

## Modules in this repository

- **[`audit-core`](audit-core)** — the framework-agnostic ledger core: append/verify logic,
  canonical JSON, SHA-256 hash chaining. Proven entirely with in-memory fakes; depends on nothing
  but the JDK and `jackson-databind`. Published as a Maven artifact, currently `0.4.0`.
- **[`audit-spring-boot-starter`](audit-spring-boot-starter)** — wires `audit-core`'s ports to
  real infrastructure: Postgres (the ledger), Mongo (the rich event store), Rabbit (ingestion),
  scheduled chain verification, scheduled anchor publishing to S3 Object Lock, metrics, health,
  cross-instance distributed ordering, and partition-ownership sharding. Published independently
  from `audit-core`, currently `0.3.1`. See its own
  [README](audit-spring-boot-starter/README.md) and [PROPERTIES.md](audit-spring-boot-starter/PROPERTIES.md).
- **`audit-demo`** — a manual-poking Spring Boot application exercising the starter end to end
  against real Postgres/Mongo/Rabbit/LocalStack containers (`docker-compose.yml`). Never
  published, has no automated tests, and carries no compatibility guarantee at any version.

See [CHANGELOG.md](CHANGELOG.md) for what each phase added and [VERSIONING.md](VERSIONING.md)
for how version numbers get decided.

## Current state (after Phase 9 — library scope complete)

Everything below is built, tested, and manually verified end-to-end via `audit-demo`'s
docker-compose loop. **Phase 9 is the last purely-`audit-java` phase** — see "Roadmap" below for
what that means going forward.

- Tamper-evident hash-chained ledger (`audit-core`), with real-database tamper detection proven
  via a raw-JDBC bypass technique (not just at the library level).
- Postgres persistence with `REVOKE UPDATE/DELETE` enforced through a real restricted DB role,
  not just application-level discipline.
- Mongo-backed rich event store (full payload, which the Postgres ledger deliberately never
  stores).
- Rabbit-based event ingestion, with cross-instance partition-ownership sharding
  (`PartitionShardResolver`) and a bounded, dead-letter-backed safeguard against a
  misconfigured-topology rejection loop.
- **Cross-instance distributed ordering**: a Postgres advisory lock plus an in-lock freshness
  check replaced Phase 2's JVM-local lock, proven via genuinely independent
  `ApplicationContext`s, not just separate threads in one JVM. Jittered retry backoff
  measurably cut a 100-way-contention retry-exhaustion rate from ~69% to ~5–9%.
- Scheduled, daily, full-catalog chain verification with break notification.
- Scheduled, daily anchor publishing to S3 Object Lock (Compliance mode), with per-partition
  failure isolation.
- A single `AuditMetricsRecorder` abstraction across every metrics-emitting call site,
  Micrometer-optional throughout.
- A cache-only health indicator that never triggers verification/anchoring itself.
- Config validation that fails fast on a specific, narrow class of misconfiguration.
- Both publishable modules are real, independently-versioned Maven artifacts with a strict
  Javadoc gate wired into publishing.
- Two ADRs record the reasoning behind decisions that would otherwise need rediscovering:
  [`docs/adr/0001`](docs/adr/0001-distributed-ordering-advisory-locks.md) (why advisory locks,
  not a new coordination system) and
  [`docs/adr/0002`](docs/adr/0002-aud-svc-persistence-model.md) (the recommended `AUD-SVC`
  persistence model).

**Known, honestly-scoped gaps before treating any of this as production-ready** (see
[CHANGELOG.md](CHANGELOG.md) for phase-by-phase detail):

- **S3 Object Lock has only ever been tested against LocalStack**, which accepts and stores
  retention configuration without enforcing real WORM immutability. The one security claim in
  this project that actually rejects a delete/overwrite attempt has never been verified against
  a real AWS account.
- **TLS for the real Postgres/Mongo/Rabbit connections this will run against has never been
  configured or tested anywhere in this project** — everything so far has run against
  unauthenticated local Testcontainers/LocalStack instances.
- **This repository has no real git history, by explicit choice so far.** Nothing here has
  actually been committed or tagged yet — do that (and tag `audit-core@0.4.0` /
  `audit-spring-boot-starter@0.3.1`) before treating any of this as released.
- **Production throughput at the real ~50K events/s target has never been characterized.**
  Every load test so far (Phase 2's 20 threads, Phase 8's 100/1000, Phase 9's 4-instance/20-
  partition comparison) is correctness-scale, proving the mechanisms work, not throughput-
  representative of real traffic. This can only be meaningfully measured against real CPMS
  production data — see the message-batching decision in `CHANGELOG.md`'s Phase 9 entry, deferred
  indefinitely for exactly this reason.

## Current public API

What a consumer actually types the name of, across both modules — everything else (`internal`
packages, `autoconfigure` classes, adapter implementations) is wiring you receive through
dependency injection, not a surface to call directly.

**`audit-core`** (`api`/`port` packages — see "The Dependency Rule" below):

- **Driving interfaces**: `AuditAppender` (obtained via `AuditAppender.create(...)`, including
  Phase 8's configurable-retry-bound overload), `AuditVerifier` (via `AuditVerifier.create(...)`).
- **Domain types**: `AuditEvent` (and its builder), `ChainedRecord`, `HashValue`,
  `PartitionContext`, `VerificationResult`, `AuditConstants`, plus the `enums` (`ActorType`,
  `AuditCategory`, `AuditChainStatus`) and `exception` types (`AuditCoreException`,
  `SeqConflictException`, `ChainIntegrityException`, `CanonicalizationException`).
- **Driven ports** (implement these to plug in your own storage/object-lock backend):
  `ChainRepository`, `ObjectLockPort`.

**`audit-spring-boot-starter`** (auto-configured beans you inject or override):

- **The entry point**: `AuditRecorder` — compose `PartitionRegistry` + `AuditAppender` +
  `EventStore` into one call; this is what a producer actually calls.
- **Scheduled jobs, callable on demand**: `ChainVerifierJob.runNow()`,
  `AnchorPublisherJob.runNow()` (both also run on their own cron schedule).
- **Read-only lookups**: `PartitionCatalog`, `PartitionRegistry`, `EventStore`.
- **Cross-cutting seams** (see "Extension points" below for the full list): `AuditMetricsRecorder`,
  `PartitionShardResolver`, `AuditChainHealthIndicator`.
- **Configuration**: `AuditProperties`, bound under `audit.*` — see
  [PROPERTIES.md](audit-spring-boot-starter/PROPERTIES.md) for every property.

## Extension points

Every one of these is a `@ConditionalOnMissingBean`-gated default: supply your own bean of the
same type in your application and the starter's default steps aside entirely, no starter code
changes required.

| Extension point | Default | Supply your own to... |
|---|---|---|
| `ChainRepository` (port) | `JpaChainRepository` | Back the ledger with something other than Postgres/JPA |
| `EventStore` (starter port) | `MongoEventStore` | Back the rich event copy with something other than Mongo |
| `ObjectLockPort` (port) | `S3ObjectLockAdapter` | Anchor to something other than S3 Object Lock |
| `PartitionRegistry` (starter port) | `JpaPartitionRegistry` | Change how partition genesis timestamps are tracked |
| `AuditMetricsRecorder` | `MicrometerAuditMetricsRecorder`/`NoOpAuditMetricsRecorder` | Route metrics somewhere other than Micrometer |
| `PartitionShardResolver` | `ConsistentHashPartitionShardResolver` | Replace `String.hashCode()`-based sharding with your own topology/hash strategy |
| `AuditChainHealthIndicator` | Cache-only, reads `ChainVerifierJob`/`AnchorPublisherJob`'s last summary | Report health differently, or from additional signals |
| `ChainBreakLoggingListener` | Logs at `ERROR` | React to a chain break some other way (or add a second `@EventListener` alongside it — no override needed for that) |
| `AnchorPublishFailureLoggingListener` | Logs at `ERROR` | Same, for anchor-publish failures |
| `MessageConverter` (Rabbit) | `Jackson2JsonMessageConverter` | Change the wire format `RabbitEventConsumer` expects |
| `Clock` | `Clock.systemUTC()` | Any test, or a non-UTC deployment need |

## Compatibility policy

Summarized here; see [VERSIONING.md](VERSIONING.md) for the full mechanical rule and worked
examples from every phase.

- **`audit-core`'s `api`/`port` packages are the versioned public surface** — semantic versioning
  applies: additive is a minor bump, breaking is a major bump, and — critically — *neither
  happening* means the version does not move, even to mark a milestone. `internal` carries no
  compatibility guarantee at all, structurally enforced via `module-info.java`'s export list, not
  by convention.
- **`audit-spring-boot-starter` is versioned independently from `audit-core`**, as of Phase 7 —
  separate published artifact, separate compatibility surface, same mechanical rule.
- **`audit-demo` carries no compatibility guarantee at any version.** It is never published and
  exists only to exercise the starter by hand.
- **Both `audit-core` and the starter are pre-`1.0.0`.** `1.0.0` for either is reserved for real
  external validation — an actual CPMS service depending on it in production — not a milestone
  either module has reached by internal decision alone.

## Design Principles (`audit-core`)

- **Framework-agnostic core.** `audit-core` depends on nothing but the JDK and
  `jackson-databind` (used only for its tree model, to canonicalize JSON payloads). It has no
  knowledge of Spring, JPA, messaging, or any specific consumer's domain vocabulary.
- **Ports and adapters, expressed as library layers.** Rather than organizing around
  application use-cases, the module is organized around "what's the public contract" versus "how
  it's implemented":
  - `api` — the types and driving interfaces consumers call into (`AuditAppender`,
    `AuditVerifier`, `AuditEvent`, `ChainedRecord`, ...).
  - `port` — the single driven interface (`ChainRepository`) that a consumer must implement to
    provide durable storage.
  - `internal` — the concrete implementations (`DefaultAuditAppender`, `DefaultAuditVerifier`,
    the canonicalization and hashing logic). Nothing outside this module — and nothing outside
    the `internal` package itself — is meant to reference these classes directly.
- **Hidden implementations, static factories.** `AuditAppender.create(...)` and
  `AuditVerifier.create(...)` are the only way to obtain an instance. Consumers only ever type
  the interface name.
- **Immutable domain model.** All domain types are `record`s. Raw hash bytes never cross a
  public boundary — they are always wrapped in `HashValue`, which is what makes `ChainedRecord`'s
  generated equality correct without any manual override.
- **Injected time.** All timestamps come from a `java.time.Clock` passed in at construction,
  never from a direct `Instant.now()` call inside `internal` classes — this is what makes the
  append logic deterministically testable.

## The Dependency Rule (`audit-core`)

- **`api` and `port` are the versioned public surface.** Semantic versioning applies to these
  packages: a breaking change to any type or method here is a major-version bump.
- **`internal` can change in any release without a major-version bump.** It carries no
  compatibility guarantee. This is enforced structurally (the package is not exported from
  `module-info.java`) rather than by convention alone, so a consumer cannot accidentally depend
  on it even if they try.

## Non-goals (`audit-core`)

- **No pluggable hash algorithm.** `AuditConstants.HASH_ALGORITHM` is a fixed `SHA-256`
  constant, not a configurable strategy. Changing the algorithm would invalidate every
  already-anchored record's hash chain regardless of how flexibly the code is structured, so
  there is no real benefit to buying that flexibility now — it would only add complexity in
  exchange for an option nobody can safely exercise.
- **No SDK.** This is a plain library; there is no client wrapper, no configuration loader, no
  auto-registration magic.
- **No messaging.** Nothing here publishes to or consumes from a broker.
- **No distributed sharding or cross-instance coordination knowledge, still — and this remains
  correct, not merely unaddressed.** `audit-core` itself has no idea whether it's running in one
  instance or fifty; `DefaultAuditAppender`'s only concession is a *configurable* retry bound
  (Phase 8, additive), not any awareness of locks or topology. Real cross-instance ordering
  (Postgres advisory locks) and partition-ownership sharding (`PartitionShardResolver`) were
  built in Phases 8–9 entirely in the Spring Boot starter's adapters
  (`JpaChainRepository`, `RabbitEventConsumer`) — exactly where that responsibility belongs, per
  the ports-and-adapters boundary above: `audit-core` defines the algorithm, the adapter owns how
  storage/messaging infrastructure is actually coordinated.

## A written decision: payload confidentiality is not this library's job

`AuditEvent.payload` is a raw `Map<String, Object>` — this library never encrypts, redacts, or
otherwise inspects its contents, at any layer, in either `audit-core` or the Spring Boot starter.
This has been true since Phase 1 and has never changed, but until now it was only ever an
implicit consequence of what the code happens to do, not a decision anyone could read, agree
with, or override. Writing it down explicitly:

- **Encryption-at-rest for the payload is the storage layer's responsibility, not this
  library's.** In the starter, that's Postgres (which never receives the payload at all — see
  `audit_immutable`'s schema) and Mongo (which does, via `EventStore`/`MongoEventStore`).
  Configuring Mongo's encryption-at-rest (or a client-side field-level encryption scheme, if a
  consuming service's payloads contain data restricted enough to need it) is entirely the
  deploying application's / infrastructure team's decision to make and configure — this library
  has no opinion on it and no mechanism to enforce it.
  - **If a producer's payloads can contain PII or data in a restricted classification tier**,
    that producer is responsible for either redacting/tokenizing it *before* calling
    `AuditRecorder.record(...)`, or ensuring the underlying Mongo deployment's encryption meets
    that data's actual classification requirements. This library will not do either of those
    things silently on a producer's behalf — a library that silently strips or transforms fields
    a caller explicitly passed in would be a worse, more surprising failure mode than one that
    stores exactly what it was given and says so plainly here.
  - This applies equally to the anchor objects `S3ObjectLockAdapter` publishes: they carry a
    partition key, a tip hash, and a timestamp — never the payload — so this boundary doesn't
    extend to anchoring at all.
- **Why this is the right boundary, not just the convenient one:** the payload's actual
  sensitivity is a fact about the *producer's* domain (what a given service chooses to put in
  `payload`), not something `audit-core` or the starter can infer generically across every
  consumer. A generic encryption mechanism built into this library would either be wrong for some
  producers (too weak for their classification tier) or unnecessary overhead for others (data
  that was never sensitive to begin with) — the producer is the only party positioned to know
  which is true for its own events.

## Roadmap

**Phases 1–9 are the complete scope of this library.** Generic capability development pauses
here — deliberately, not because nothing more could be imagined. From this point forward, changes
to `audit-core`/`audit-spring-boot-starter` should be driven by what actually integrating them
into `AUD-SVC`/`AUDIT-SVC` and running them under real traffic reveals, not by further speculation
about what a library "should" eventually support. **Everything after this point lives in the
CPMS repo, not here.**

1. ✅ **`audit-core`** *(Phase 1)* — the framework-agnostic ledger core: append/verify logic,
   canonical JSON, hash chaining, proven with in-memory fakes.
2. ✅ **A Spring Boot starter** *(Phases 2–7)* — wires `audit-core`'s ports to real
   infrastructure: JPA/Postgres ledger and partition registry (Phase 2), Mongo rich event store
   (Phase 3), Rabbit ingestion (Phase 4), scheduled chain verification (Phase 5), scheduled S3
   Object Lock anchoring (Phase 6), and metrics/health/config-validation hardening plus real
   Maven publishing for both modules (Phase 7).
3. ✅ **Distributed ordering** *(Phase 8)* — the bounded, single-JVM-only retry in
   `DefaultAuditAppender` was always a Phase-1 placeholder (see "Non-goals" above); replaced
   `JpaChainRepository`'s in-JVM lock with a Postgres advisory lock plus in-lock freshness check,
   proven cross-instance via genuinely independent `ApplicationContext`s. A follow-up jittered
   backoff fix measurably cut a 100-way-contention retry-exhaustion rate from ~69% to ~5–9%.
4. ✅ **Sharding, and closing out the library scope** *(Phase 9)* — `PartitionShardResolver`
   (partition-ownership filtering at the Rabbit-consumer level, default no-op for every
   single-instance deployment), a measured sharded-vs-unsharded comparison, an explicit
   (deferred) message-batching decision, and `docs/adr/0002` resolving the long-open `AUD-SVC`
   persistence question.
5. **`AUDIT-SVC`** *(CPMS repo)* — a dedicated audit service that owns per-tenant chain storage
   and exposes the ledger over a network boundary.
6. **`AUD-SVC`** *(CPMS repo)* — platform audit, per `docs/adr/0002`'s recommendation reusing
   this same stack with a single global partition; a downstream consumer building verification,
   export, and reporting workflows on top of `AUDIT-SVC`.
7. **Producer rollout** *(CPMS repo)* — migrating existing services to emit audit events through
   this stack.
8. **Further hardening** *(CPMS repo, informed by real production experience)* — real-AWS S3
   Object Lock validation, TLS on real infrastructure connections, real git history and release
   tagging, production throughput characterization, and the deferred message-batching decision if
   real traffic data ever justifies revisiting it (see "Known, honestly-scoped gaps" above).

## Building

```
./gradlew build              # all modules
./gradlew :audit-core:build
./gradlew :audit-spring-boot-starter:build
```
