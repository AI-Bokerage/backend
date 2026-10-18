# Changelog

All notable changes to this repository are recorded here. See [VERSIONING.md](VERSIONING.md) for
how version numbers are decided.

## Release-readiness audit (post-Phase 9)

- **Retroactive version correction for `audit-spring-boot-starter`**: Phase 8 and Phase 9 each
  added additive public surface to this module (three new `AuditMetricsRecorder` methods in
  Phase 8; `sharding.PartitionShardResolver` plus two more `AuditMetricsRecorder` methods in
  Phase 9) without bumping the version at the time — an oversight caught by this audit, not a
  deliberate exception to `VERSIONING.md`'s mechanical rule. Corrected: `0.1.0` → `0.2.0` (Phase
  8) → `0.3.0` (Phase 9's sharding surface) → `0.3.1` (Phase 9's message-converter fix, a bug fix
  to `RabbitEventConsumer`'s internal listener wiring rather than a documented-surface change, so
  patch rather than minor). See `VERSIONING.md` for the full reasoning.
- **Closed a real configuration-validation gap**: `audit.rabbit.shard.total-instances=0` — a
  value nothing previously rejected — reached `ConsistentHashPartitionShardResolver`'s
  `Math.floorMod` and threw an `ArithmeticException` on the first inbound message, rather than
  failing clearly at startup. Root cause: the `@Valid` cascade Jakarta Bean Validation requires at
  every level of a nested `@ConfigurationProperties` graph was broken at the very first level —
  `AuditProperties.rabbit` had no `@Valid`, so nothing beneath it, including `Shard`, was ever
  validated regardless of what constraints it declared. Fixed by adding `@Valid` to both the
  `rabbit` and `shard` fields and `@Min(0)`/`@Min(1)` to `Shard`'s `instanceIndex`/
  `totalInstances`, proven by two new tests in `AuditPropertiesValidationTest`. Only takes effect
  if a JSR-380 validator is on the *consuming* application's classpath, same caveat as every other
  constraint on this class.
- **Closed a real startup-failure gap affecting every published version back through `0.1.0`**,
  found only by actually depending on `audit-spring-boot-starter`'s real Maven coordinate from a
  brand-new, unrelated project — every test inside this repository happens to already carry
  `spring-boot-starter-validation`, so nothing here had ever exercised this path. This starter
  declared `jakarta.validation:jakarta.validation-api` as `implementation` (needed so the
  `@Min`/`@Valid`/`NotBlankIfPresent` annotations on `AuditProperties` remain resolvable via
  reflection even without a real validator), which put that API unconditionally on every
  consumer's *runtime* classpath. Spring Boot's own `ValidationAutoConfiguration` activates
  merely because the API's interfaces are visible — regardless of whether an actual JSR-380
  provider (Hibernate Validator) exists — and then fails hard while building its own default
  validator, taking down `AuditProperties`' own `@ConfigurationProperties` binding with it: "The
  Bean Validation API is on the classpath but no implementation could be found," contradicting
  this starter's own documented promise that validation "silently does nothing" without a
  validator. Fixed by changing this one dependency to `compileOnly` — the annotation classes
  remain resolvable for any consumer who *does* add a validator (almost always transitively, via
  `spring-boot-starter-validation`, which bundles both the API and Hibernate Validator together),
  while a consumer with neither no longer has the API leaked onto their classpath at all, which is
  exactly the condition Boot's own `ValidationAutoConfiguration` correctly treats as inactive.
  Verified by actually starting a fresh, unrelated Spring Boot application against the real
  published artifact (not `project(":audit-core")`), with and without a validator, and confirming
  an event round-trips through both the Postgres ledger and Mongo event store correctly either way.
- Extended `ArchitectureRulesTest`'s anchor-isolation test to also cover the `scheduling` package
  (home to `AnchorPublisherJob`), which had never been added to any isolation check despite being
  introduced the same phase as `anchor` itself — a coverage gap, not a live violation; no actual
  cross-package import existed.
- **Closed a real gap: `AuditEventDocument`'s `@Indexed` fields (`partitionKey`, `eventHash`) had
  never actually created a real index in Mongo, at any phase.** `@Indexed` is source-level
  metadata only — Spring Data Mongo does nothing with it unless
  `spring.data.mongodb.auto-index-creation=true` is set on the *consuming* application, which
  nothing in this starter ever set. Confirmed by querying a real running Mongo instance's index
  catalog via `listIndexes()`: only the default `_id` index existed. Fixed by explicitly
  resolving and creating `AuditEventDocument`'s own indexes in `AuditMongoAutoConfiguration`
  (`IndexResolver` + `IndexOperations`, scoped to exactly this document, not a global
  `auto-index-creation=true` flip that would also apply to a consuming application's unrelated
  Mongo documents) — proven by a new `MongoEventStoreTest` assertion against the real index
  catalog, not merely that the annotation compiles.
- **That fix's first version introduced a real regression of its own, caught by re-running this
  starter's own load tests, not assumed safe because it compiled and one test passed:** creating
  indexes eagerly at context startup made the *entire* Spring context fail to start if Mongo
  wasn't reachable at that exact moment — broke `PartitionLoadTest`/`ShardedLoadTest`, neither of
  which configures a real Mongo connection since they only exercise the Postgres-backed ledger.
  `AuditMongoAutoConfiguration` only ever conditioned on `MongoRepository` being on the classpath,
  never on Mongo actually being reachable, so this eager failure was a strictly worse regression
  than the silent missing-index gap it fixed. Corrected: index creation now catches any failure
  and logs a `WARN` instead of propagating — an index is a performance aid this library doesn't
  depend on for correctness, not something worth failing an entire application's startup over.

- **New `FullPipelineIntegrationTest`, genuinely new coverage** ("re-run load tests" is not the
  same as "prove every mechanism works together in one scenario," which nothing before this did):
  two genuinely independent instances sharing one real Postgres/Mongo/Rabbit/LocalStack stack,
  partition-ownership sharding across them, concurrent event ingestion across 10 partitions, both
  `ChainVerifierJob` and `AnchorPublisherJob` manually triggered, one partition tampered via raw
  JDBC, and both the health indicator and metrics checked against the outcome — run twice, once
  with Micrometer present and once with it made absent from that JVM's view via
  `FilteredClassLoader` (the same technique `ApplicationContextRunner.withClassLoader(...)` uses
  internally), extending Micrometer-absence verification beyond the manual `audit-demo` check
  earlier phases relied on. Found and fixed two real issues while building it, neither hypothetical:
  concurrent publishing across many partitions on one Rabbit queue shared by two competing
  consumers can legitimately exceed the shard-mismatch redelivery cap and dead-letter a message —
  an accepted, documented tradeoff of that mechanism, not a bug, handled here by redelivering
  anything found dead-lettered, the same recovery a real operator would perform; and
  `spring-boot-starter-actuator`'s own built-in metrics auto-configuration creates two competing
  `MeterRegistry` beans when no explicit registry is supplied and this starter's own Micrometer
  wiring is excluded, requiring an explicit `spring.autoconfigure.exclude` for that one scenario.

## Phase 9

**This is the last purely-`audit-java` phase.** Generic capability development pauses here —
further changes should be driven by real `AUD-SVC`/`AUDIT-SVC` integration experience, not
speculation. See the root README's updated Roadmap.

- **ADR-0002** (`docs/adr/0002-aud-svc-persistence-model.md`, documentation only, no version-bump
  implication): resolves the AUD-SVC persistence design question that had been open since before
  any code in this repository existed. Recommends (not mandates — every claim is phrased as "the
  recommended initial implementation is...") that `AUD-SVC` reuse the exact same
  `audit-core`/`audit-spring-boot-starter` stack `AUDIT-SVC` uses, with a single global partition
  (not one per tenant) for the platform audit chain — `tenantId` is a reference `PlatformAuditEvent`
  carries, not a partitioning dimension, so it belongs in `AuditEvent.resourceType`/`resourceId`,
  queryable in the rich Mongo copy, not the hash-chain partition key.
- **Phase 9A — `sharding.PartitionShardResolver`**: the generic mechanism answering "does this
  instance own this partition," consulted by `RabbitEventConsumer` before ever calling
  `AuditRecorder.record(...)` for a partition this instance doesn't own.
  `ConsistentHashPartitionShardResolver` uses `String.hashCode()` modulo the configured instance
  count — a documented pragmatic v1 choice (mediocre distribution versus a purpose-built hash,
  accepted rather than adding a dependency with no evidence yet that production keys skew).
  Defaults (`audit.rabbit.shard.instance-index=0`, `audit.rabbit.shard.total-instances=1`) are a
  deliberate no-op: every single-instance deployment, including `audit-demo`, keeps working with
  zero configuration changes — confirmed by actually running `audit-demo` against real
  Postgres/Mongo/Rabbit/LocalStack containers, not merely by compiling it.
- A partition this instance doesn't own is republished to the same queue with a custom, self-managed
  retry-count header (not native RabbitMQ `x-death`, since this starter's main queue has never been
  declared by the starter itself — Phase 4's "queue provisioning is an application-level concern"
  boundary — so there is no queue this class could attach a self-referencing dead-letter-exchange
  argument to). Once that counter exceeds 5 redeliveries, the message is redirected to a
  starter-declared dead-letter queue (`<queue>.dead-letter`) with an `ERROR` log instead of being
  rejected again — proven by `ShardMismatchDeadLetterTest` to actually stop the loop, not merely
  that dead-lettering is configured. `AuditMetricsRecorder` gained `recordShardOwnershipAccepted`
  and `recordShardMismatch`.
- **A real regression was found and fixed while proving `audit-demo` still works with zero config
  changes, not merely by compiling it**: taking a raw `Message` parameter (needed to inspect the
  partition key before deciding ownership) and calling `messageConverter.fromMessage(rawMessage)`
  manually lost the type-inference `@RabbitListener(AuditEventEnvelope envelope)`'s own parameter
  type used to provide — silently requiring every producer to set a Spring-specific `__TypeId__`
  header that only Spring's own `convertAndSend` ever sets automatically, breaking any external,
  non-Spring producer that previously worked. Fixed by passing an explicit
  `ParameterizedTypeReference` conversion hint via `SmartMessageConverter` (the plain `Class`
  overload is silently ignored by `Jackson2JsonMessageConverter` — confirmed by reading its actual
  source after a first fix attempt using `Class` still failed identically, not assumed from the
  method signature alone) — found by actually publishing a raw envelope with no `__TypeId__`
  header via `audit-demo`'s real RabbitMQ container, exactly the discipline this project has
  applied at every prior phase.
- **Phase 9B — measured, not assumed: does sharding help?** `ShardedLoadTest` extended Phase 8's
  load-test pattern to 4 simulated independent instances and 20 distinct partitions (versus Phase
  8's 1), comparable total volume (1000 events), run twice — once with no ownership routing
  (today's Phase 8 behavior), once with `ConsistentHashPartitionShardResolver` routing active.
  **Measured across 3 runs**: both configurations reached 0% retry-exhaustion at this scale
  (spreading load across 20 partitions instead of 1 already dramatically reduces contention
  versus Phase 8's deliberately-adversarial single-partition stress test) — but sharded routing
  was consistently faster: p50 append latency ~4–8ms unsharded versus ~3–4ms sharded, p99
  ~1.8–1.9s unsharded versus ~0.5–0.6s sharded (roughly 3× lower tail latency). Both scenarios
  produced a fully consistent, verifiable chain for every partition.
- **The batching decision (`WRITE_BATCH_SIZE`), recorded here, not as code — no batching code was
  written regardless of outcome, per this phase's explicit instruction not to implement it under
  any measured result:** deferred indefinitely. The measured evidence above shows sharding plus
  Phase 8's jitter fix already bring contention and latency to a good place at the scale this
  local environment can actually test — but that same environment cannot faithfully represent the
  real 50K events/s production target, so no local measurement, however clean, can actually
  justify writing batching code now. Revisit only against real CPMS production traffic data, as
  its own distinct future phase.

## Phase 8 follow-up: jittered backoff

- **`audit-core` internal-only change, no version-bump implication** (per `VERSIONING.md`: changes
  confined to the `internal` package carry no compatibility-surface implication): `DefaultAuditAppender`
  now sleeps for a small, randomized, exponentially-growing delay before each retry (full-jitter
  backoff, base 5ms, cap 200ms) — addressing the actual *cause* of Phase 8's measured 69%
  retry-exhaustion rate at 100-way contention (contenders recolliding in lock-step every round),
  not just papering over the *symptom* with a larger retry budget.
- **Measured effect**, same `PartitionLoadTest` scenario, `audit.jpa.append-max-attempts` still 20:
  retry-exhaustion rate dropped from ~69% to **~5–9%**; p50 append latency for successful attempts
  dropped from ~700–740ms to single-digit milliseconds. p99 stayed comparable (~2.2–3.3s).
- **Re-evaluated `append-max-attempts=20`, not merely assumed it should now drop**: actually tried
  8 (flaky, 1/10 failures on the 20-thread regression test) and 12 (reliable, 10/10, but ~24–28%
  100-way exhaustion — materially worse than 20's ~5–9% for no latency/resource benefit). **Kept
  20** — jitter made the existing budget more effective, not merely enabled a smaller one; spending
  the budget down just to prove a lower number survives once wasn't a good trade. Full comparison
  table and reasoning in `docs/adr/0001-distributed-ordering-advisory-locks.md`.

## Phase 8

**This phase proves distributed correctness only. It does not establish production throughput.**
Message batching, partition-to-instance assignment, and Rabbit consumer concurrency tuning remain
separate, later, application-level concerns.

- **`audit-core` 0.4.0** (minor, additive — evidence-based, not preemptive): added
  `AuditAppender.create(ChainRepository, Clock, int maxAttempts)`, an overload of the existing
  factory letting a caller configure the bounded-retry count instead of the hardcoded default of
  3. The default (`create(repository, clock)`) is unchanged. This was **not** made speculatively —
  see "Measured evidence" below for the regression that forced it.
- **Replaced `JpaChainRepository`'s Phase 2 in-JVM `ReentrantLock` with a Postgres advisory lock**
  (`pg_advisory_xact_lock(hashtext(partitionKey))`), closing the cross-instance race the JVM-local
  lock never could: two independent `AUDIT-SVC` instances, each its own JVM, could both read the
  same partition tip and race to append, with nothing stopping it before this phase. The JVM lock
  is removed entirely, not kept alongside the new mechanism — confirmed by grep, no
  `ConcurrentHashMap`/`Lock` field remains in that class.
- **The lock alone only narrows the race window; it does not close it.** `append()` now re-reads
  the partition's actual tip while holding the lock and compares it against what the caller's
  `ChainedRecord` was computed from, rejecting a stale caller via `SeqConflictException`
  *before* any insert is attempted — proven by `StalenessRejectionTest`, which holds the lock open
  via a raw JDBC connection and confirms the rejected attempt never reaches the database, not
  merely that it eventually fails via the `UNIQUE` constraint.
- Added `audit.jpa.partition-lock-timeout` (default 5s, applied via `SET LOCAL lock_timeout` and,
  for the same cost, `SET LOCAL statement_timeout`), proven by `LockTimeoutTest` to actually bound
  a stuck transaction's blast radius — not immediate, not indefinite.
- Added `audit.jpa.append-max-attempts` (default **20**, not `audit-core`'s bare default of 3 —
  see "Measured evidence" below).
- Extended `AuditMetricsRecorder` with `recordPartitionLockWait`, `recordStalenessRejection`, and
  `recordLockTimeout` — all three have real callers in `JpaChainRepository`, introduced by this
  phase's exact new failure/contention modes, the same justified-by-a-real-caller standard as
  every prior interface addition. `DEBUG`-level logging includes partition key, lock wait
  duration, and a per-thread retry-attempt counter on every `append()` call.
- **Measured evidence, not assumption, for why `append-max-attempts` defaults to 20:**
  re-running Phase 2's 20-thread single-JVM concurrency test against the new advisory-lock
  mechanism with the unchanged 3-attempt default failed with **17 of 20 threads exhausting their
  retries** — the identical failure mode Phase 2 first found, reproduced under a different
  coordination mechanism, because the underlying protocol (two uncoordinated `findTip()`/
  `append()` calls) is unchanged. Raising the configured value to 20 made that test pass reliably
  across repeated runs. `PartitionLoadTest` (100 concurrent appenders, 2 independent instances,
  1000 total events against one partition — deliberately more extreme than the correctness bar
  this default needs to clear) then measured, purely informationally: **~69% retry-exhaustion
  rate** (roughly 309/1000 events succeeding per run, consistent across 3 runs), p50 append
  latency ~700–740ms, p99 ~2.8–3.2s. This number is reported, not chased — raising the retry
  budget further would only relocate where a higher contention level's exhaustion rate lands, not
  fix the structural cause (see the ADR). 20 remains the default because it is the smallest value
  that reliably clears this starter's own correctness regression test, which is the actual bar
  this phase needs to clear.
- Added `docs/adr/0001-distributed-ordering-advisory-locks.md`, covering: why advisory locks over
  Redis/ZooKeeper/etcd; why this is additional pessimistic coordination layered on the existing
  Phase 1 optimistic `UNIQUE`-constraint-plus-retry mechanism, not a replacement for it; why not
  `SERIALIZABLE` isolation instead; the recorded two-call-protocol tradeoff (a cleaner fix would
  have `ChainRepository` own read-decide-write atomically, a breaking port change deliberately
  deferred to a future major version); and the `hashtext()` 32-bit collision evolution path.
- New tests: `CrossInstanceOrderingTest` (two genuinely independent Spring
  `ApplicationContext`s — no shared Java object beyond the test method itself — both pointed at
  the same Postgres, proving real cross-instance coordination, not an accidentally-still-JVM-local
  mechanism), `StalenessRejectionTest`, `LockTimeoutTest`, `PartitionLoadTest`. No new package was
  introduced this phase, so `ArchitectureRulesTest` needed no changes.

## Phase 7 (Part B: publishing and release)

- **`audit-core` stays at `0.3.0`. No code or API change this part — pure packaging — so no
  version change, applying the versioning rule mechanically rather than bumping it to mark "first
  publish" as a milestone.** `audit-spring-boot-starter` gets its **first explicit version,
  `0.1.0`**, not `1.0.0` — it never had a version before this part, so there is no prior release
  for this to be additive or breaking relative to; `1.0.0` for either module still waits for real
  external validation (an actual CPMS service depending on it in production).
- Both modules are now real, independently-versioned Maven artifacts (`maven-publish`, applied
  only to modules with the `java-library` plugin via `plugins.withId("java-library")` — this
  automatically excludes `audit-demo`, which applies the Spring Boot application plugin instead,
  confirmed by a required test that it has no publish-family Gradle task at all). Shared POM
  convention (org/license/developer placeholders, to be confirmed for real before an actual
  release) lives once in the root `build.gradle.kts`, not duplicated per module.
- The publish repository is resolved via `resolveCredential()`: a Gradle property first (so a
  developer's own `~/.gradle/gradle.properties`, outside version control, works for local
  `publishToMavenLocal`/manual releases), falling back to an environment variable (what CI
  actually uses via secrets) — never a hardcoded vendor URL, since which registry CPMS's builds
  can actually reach isn't something this repository can know. `./gradlew publish` (the task that
  actually touches a remote repository) fails with a clear, specific message if neither is set;
  `./gradlew publishToMavenLocal` and every other task remain unaffected — confirmed by an actual
  failing/passing test in each direction, not assumed. (Getting this right took two attempts:
  leaving `credentials.username`/`password` null when unset made Gradle's own task-input
  validation fail first, with a generic "doesn't have a configured value" error that pre-empted
  this build's own clear message entirely — fixed by placeholder-defaulting those too, same as
  the URL, and gating the *actual* check in a `doFirst` block on `PublishToMavenRepository`
  specifically, Gradle's own task type for exactly that, never `PublishToMavenLocal`.)
- `./gradlew javadoc` now fails on any Javadoc *warning*, not only hard errors
  (`-Xwerror`/`-quiet`), for both publishable modules, wired as a dependency of the `publish`
  task itself (not only mentioned in the illustrative `.github/workflows/release.yml`, so a local
  `./gradlew publish` enforces it too). This immediately, retroactively caught 22 pre-existing
  "default constructor without a comment" warnings across the starter — every auto-configuration
  class, mapper, listener, and `AuditProperties`'s nested property groups had relied on an
  implicit default constructor since whichever phase introduced them; `audit-core` had none,
  since Phase 1 already held itself to this discipline throughout.
- Added `.github/workflows/release.yml` (tag-triggered, illustrative only — the Gradle
  configuration is the source of truth; adapt the trigger/runner/secrets wiring to this org's
  actual CI/CD system, per the file's own header comment).
- Verified directly, not just by inspecting the build script: `./gradlew publishToMavenLocal`
  succeeds for both modules, and the starter's generated POM correctly lists
  `com.company.audit:audit-core:0.3.0` as a `compile`-scope dependency — Gradle's `maven-publish`
  plugin translating the internal `project(":audit-core")` reference into that coordinate
  automatically, exactly as expected, with no change to how this repository builds itself.

## Phase 7 (Part A: hardening)

- **`audit-core` untouched this phase** — Part A is starter-only, no `audit-core` API change.
- Added `AuditProperties`'s `@Validated` + JSR-380 constraints, most notably a custom
  `NotBlankIfPresent` constraint on `audit.anchor.bucket` — deliberately not `@NotBlank`, which
  would reject `null` too and break every application that never intends to use anchoring at
  all (the overwhelming default, since that property has no default value). `null` (never
  configured) passes; a blank/whitespace-only value fails fast at startup with a message naming
  the property, instead of surfacing later as an unrelated AWS region-resolution error deep
  inside `S3ObjectLockAdapter`. Documented plainly that `@Validated` does nothing at all unless a
  JSR-380 validator (e.g. `spring-boot-starter-validation`) is on the *consuming* application's
  classpath — the same explicit-limitation discipline already applied to the LocalStack WORM
  caveat in Phase 6.
- Added `AuditMetricsRecorder`, a single interface (`recordMessageConsumed`, `recordAppend`,
  `recordVerification`, `recordAnchor`) replacing the Rabbit-consumer-only ad hoc callback from
  Phase 4, backed by `MicrometerAuditMetricsRecorder` when Micrometer is present and
  `NoOpAuditMetricsRecorder` otherwise (`AuditMetricsAutoConfiguration`, unconditional at the
  class level so a recorder bean always exists). All four call sites —
  `RabbitEventConsumer`, `JpaChainRepository`, `ChainVerifierJob`, `AnchorPublisherJob` — now
  depend on this plain interface; `MicrometerAuditMetricsRecorder` is the only class in the
  entire starter permitted to name `MeterRegistry`, for the same class-loading reason discovered
  in Phase 4. Verified empirically (not just on paper) by running `audit-demo` with Micrometer
  absent from its classpath.
- Added `AuditChainHealthIndicator` (`com.company.audit.spring.health`,
  `AuditHealthAutoConfiguration`), reporting `UP`/`DOWN` purely from `ChainVerifierJob`'s cached
  last-run summary (kept in an `AtomicReference`, updated at the end of every `runNow()`) —
  never by triggering verification itself. Reports `UNKNOWN`, not a false `UP`, before any run
  has completed. Also surfaces `AnchorPublisherJob`'s last-run timestamp as a detail (looked up
  via `ObjectProvider` since anchoring is optional infrastructure), without anchoring outcomes
  affecting the reported status. **Verified against the actually-resolved dependency, not
  assumed**: Boot 4 moved the entire health-contributor API
  (`HealthIndicator`/`Health`/`Status`) out of `spring-boot-actuator` into a new
  `spring-boot-health` module — confirmed by inspecting the resolved
  `spring-boot-actuator-4.0.6.jar` and finding no `org.springframework.boot.actuate.health`
  package there at all.
- Added `audit-spring-boot-starter/PROPERTIES.md`, consolidating every `audit.*` property
  introduced across all seven phases — previously scattered across each phase's Javadoc and this
  file's own history — into one reference table naming each property's default and the
  auto-configuration class that gates it.
- Extended `ArchitectureRulesTest` for the new `metrics` package: unlike every other pairwise
  isolation rule in this file (mutual — neither side may import the other), this one is
  deliberately one-directional — `persistence.jpa`, `persistence.mongo`, `messaging`,
  `verification`, and `anchor` all depend on the plain `AuditMetricsRecorder` interface, but
  `metrics` itself must never import back into any of them.
- Re-ran the full existing test suite, including Phase 6's auto-configuration ordering fixes
  (`AuditVerificationAutoConfiguration(after = AuditCoreAutoConfiguration.class)`,
  `AuditAnchorAutoConfiguration`'s `@ConditionalOnProperty` gate) — still green, unmodified.

## Phase 6

- **`audit-core` 0.3.0** (minor, additive): added `port.ObjectLockPort`, a driven port for
  publishing a WORM anchor of a partition's chain tip to immutable object storage. Returns an
  opaque `String` storage reference, not a rich AWS-shaped type — S3-specific concepts (ETag,
  VersionId) would leak infrastructure into a module that's supposed to know nothing about it.
  Deliberately just the interface: no `AnchorPublisher`, no default implementation, and no
  orchestration class in `audit-core` — per the correction from the Phase 4/5 review, "find tip,
  call port" is orchestration, not algorithm, and belongs in the starter next to `AuditRecorder`,
  not in core next to `AuditVerifier`. Lives in the already-exported `port` package, so
  `module-info.java`'s export list is unchanged.
- Added the starter's anchoring pieces on top of that port: `AnchorPublisher` (single-partition
  orchestrator, needs neither `PartitionRegistry` nor `PartitionCatalog` — just the chain's own
  tip), `S3ObjectLockAdapter` (writes one append-only object per publish, keyed by partition and
  timestamp so a prior anchor is never overwritten, with configurable Object Lock retention),
  `AnchorPublisherJob` (daily cron, reusing `PartitionCatalog` from Phase 5 — no new enumeration
  mechanism), and the matching `AnchorPublishFailedEvent`/`AnchorPublishFailureLoggingListener`
  pair, following the same per-partition-failure-doesn't-abort-the-job discipline established by
  `ChainVerifierJob`.
- **Verified, not assumed: the AWS SDK v2 version is not managed by Spring Boot's dependency
  BOM.** Imported `software.amazon.awssdk:bom` separately for consistent module versions.
- `audit-demo` gained a LocalStack service and `POST /demo/admin/anchor-all`, completing the
  manual loop: post events, tamper one, `verify-all` to see the break, `anchor-all` to anchor the
  other (clean) partitions' current tips.

## Phase 5

- Added scheduled chain verification: `PartitionCatalog` (a new, separate interface from
  `PartitionRegistry` — enumeration is a read-only catalog query, `resolve()` is a
  lifecycle/write-capable operation, and a verification job has no business invoking the latter)
  with `JpaPartitionCatalog`, `ChainVerifierJob` (daily cron, default `0 0 2 * * *`, exposed as a
  plain `runNow()` method independent of the schedule), `AuditChainBreakDetectedEvent` (a plain
  object published via Spring's own `ApplicationEventPublisher` — no bespoke listener port),
  `ChainBreakLoggingListener` (its own top-level, independently-testable bean, logging at `ERROR`,
  replaceable wholesale via `@ConditionalOnMissingBean`), `VerificationSummary`, and
  `AuditVerificationAutoConfiguration`.
- Verification runs strictly sequentially, partition by partition, by design — documented in
  `ChainVerifierJob`'s Javadoc and the starter's new README, so a future contributor doesn't
  casually parallelize the loop without first thinking through ordering and database load.
- `audit-demo` gained `POST /demo/admin/verify-all`, manually verified end-to-end: posted events
  across three partitions, tampered one via raw JDBC, and confirmed the response reports the
  break with correct `breakAtSeq`/timestamps, alongside the expected `ChainBreakLoggingListener`
  `ERROR` log line in the console.
- `audit-core` untouched this phase.

## Phase 4

- Added the Rabbit consumer: `AuditEventEnvelope` (wire format, with a `schemaVersion` marker
  and a generic `headers` escape hatch), `AuditEventEnvelopeMapper` (parses `actorType`/
  `category` strings into real enums, raising `UnrecognizedEnvelopeValueException` on an
  unrecognized value), `RabbitEventConsumer` (`@RabbitListener`, no manual ack/nack — relies on
  Spring AMQP's default nack-and-requeue on failure), and `AuditRabbitAutoConfiguration`.
- **Verified, not assumed: Spring AMQP in this Boot 4.0.6 baseline is Jackson-2-based.**
  `spring-boot-starter-amqp` resolves `com.fasterxml.jackson.core:jackson-databind` (Jackson 2),
  confirmed by inspecting `compileClasspath`. `Jackson2JsonMessageConverter` is used
  accordingly, even though `spring-amqp` 4.x marks it deprecated in favor of a newer
  Jackson-3-based converter that would fail with `NoClassDefFoundError` against this classpath.
- **Discovered and fixed three more real gaps, each found via an actual failure:**
  1. Boot 4's own Jackson auto-configuration (`spring-boot-jackson`) produces a **Jackson 3**
     `ObjectMapper` (`tools.jackson.databind.ObjectMapper`) — a different class entirely from
     what `Jackson2JsonMessageConverter` needs. `AuditRabbitAutoConfiguration` builds its own
     Jackson-2 `ObjectMapper` (with `JavaTimeModule` registered for `java.time.Instant`) rather
     than injecting a Boot-managed one.
  2. Jackson needs constructor-parameter names to deserialize records; without the `-parameters`
     javac flag, `AuditEventEnvelope` failed to deserialize with `InvalidDefinitionException`.
     Added `-parameters` to every subproject's `JavaCompile` tasks in the root
     `build.gradle.kts`, matching `CPMS-Platform`'s own established convention.
  3. A `MeterRegistry`-typed field or parameter *anywhere* in a class — even one that's always
     `null` when Micrometer is absent — makes that class fail to load via reflection
     (`NoClassDefFoundError`/`TypeNotPresentException`), regardless of `@ConditionalOnBean`,
     because the JVM resolves field/parameter types eagerly. `RabbitEventConsumer` now takes a
     plain `Consumer<String>` outcome callback and is entirely unaware of Micrometer; the
     Micrometer-specific wiring lives only in a nested `@ConditionalOnClass(MeterRegistry.class)`
     configuration class, the only place in the codebase that ever names `MeterRegistry`.
- `audit-demo` gained a RabbitMQ service (docker-compose), a declared `audit.events` queue bean,
  and was manually verified end-to-end: a hand-published envelope on the Rabbit queue was
  correctly recorded in both Postgres and Mongo.

## Phase 3

- **`audit-core` 0.2.0** (minor, additive): added
  `SeqConflictException(String message, Throwable cause)`. The Phase 2 JPA adapter had been
  forced to use `initCause()` as a workaround because only a `(String)` constructor existed;
  this closes that gap properly.
- **Corrected the Java/Spring Boot baseline.** Phase 2 shipped against an assumed Java 17 /
  Spring Boot 4.1.0 baseline. The real CPMS baseline (confirmed against `CPMS-Platform`'s own
  `build.gradle.kts`) is **Java 21 / Spring Boot 4.0.6**. All modules — including `audit-core`,
  whose Phase 1 spec had originally fixed Java 17 — now target Java 21, and the starter/demo
  target Spring Boot 4.0.6. The full Phase 1/2 test suite was re-run and confirmed green under
  the corrected versions before any Phase 3 work started.
- **`PartitionRegistry.resolve` signature change** (starter-local, pre-1.0, not a semver
  violation — see VERSIONING.md): dropped the caller-supplied `fallbackCreatedAt` parameter.
  Old: `resolve(String partitionKey, Instant fallbackCreatedAt)`. New:
  `resolve(String partitionKey)`. The implementation now decides the creation instant itself via
  an injected `Clock`. Callers had no business deciding partition-creation bookkeeping; that
  concern belongs entirely to `PartitionRegistry`, not its API surface. Updated
  `JpaPartitionRegistry`, its test, and `DemoController`'s call site.
- Added the Mongo-backed rich event store (`EventStore` port, `MongoEventStore` adapter),
  `AuditRecorder` orchestrator, and `audit-demo` support for reading events back with their
  payload intact.

## Phase 2

- Added `audit-spring-boot-starter`: JPA adapters (`JpaChainRepository`, `JpaPartitionRegistry`),
  Flyway migrations at `classpath:db/migration/audit`, `AuditJpaAutoConfiguration` and
  `AuditCoreAutoConfiguration`, and the `PartitionRegistry` starter-local port.
- Added `audit-demo`, a manual-poking demo application (never published, no automated tests).
- Discovered and fixed, at the adapter level (no `audit-core` changes): `audit-core`'s fixed
  3-attempt append retry cannot survive genuine 20-way database contention on one partition —
  `JpaChainRepository` now serializes the `findTip`/`append` pair per partition within a single
  JVM via an in-process lock, closing the race that the retry bound alone could not.

## Phase 1

- Initial release of `audit-core`: the framework-agnostic, hash-chained, append-only audit
  ledger core — `AuditAppender`, `AuditVerifier`, canonical JSON, SHA-256 chain hashing — proven
  entirely with in-memory fakes.
