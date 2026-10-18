# `audit.*` properties

Every configuration property this starter binds, across every phase, in one place — this used to
be scattered across each phase's Javadoc and `CHANGELOG.md` entries; this file is the single
consolidated reference going forward. All properties are bound onto `AuditProperties`
(`com.company.audit.spring.config.AuditProperties`), prefix `audit`.

| Property | Default | Bound by | Gates / used by |
|---|---|---|---|
| `audit.jpa.enabled` | `true` | `AuditProperties.Jpa` | `AuditJpaAutoConfiguration` — `@ConditionalOnProperty(prefix = "audit.jpa", name = "enabled", havingValue = "true", matchIfMissing = true)`. Set to `false` to disable the JPA-backed ledger/partition-registry adapters entirely. |
| `audit.jpa.partition-lock-timeout` | `5s` | `AuditProperties.Jpa` | How long `JpaChainRepository.append` waits to acquire a partition's Postgres advisory lock (`SET LOCAL lock_timeout`) before failing with `SeqConflictException`; the same value also bounds the transaction's `statement_timeout`. |
| `audit.jpa.append-max-attempts` | `20` | `AuditProperties.Jpa` | The retry bound `AuditAppender` (wired by `AuditCoreAutoConfiguration`) is configured with — deliberately higher than `audit-core`'s own bare default of 3, based on measured evidence; see `docs/adr/0001-distributed-ordering-advisory-locks.md`. |
| `audit.mongo.collection` | `audit_events` | `AuditProperties.Mongo` | The Mongo collection `AuditEventDocument` is stored in (`@Document(collection = "${audit.mongo.collection:audit_events}")`). Read by `AuditMongoAutoConfiguration`'s adapter, not by a `@ConditionalOnProperty` gate — Mongo auto-activates whenever `MongoRepository` is on the classpath. |
| `audit.rabbit.queue` | `audit.events` | `AuditProperties.Rabbit` | The queue `RabbitEventConsumer.onMessage` listens on (`@RabbitListener(queues = "${audit.rabbit.queue:audit.events}")`), wired by `AuditRabbitAutoConfiguration`. Also determines the dead-letter queue's name: `<queue>.dead-letter`, fixed convention, not independently configurable. |
| `audit.rabbit.shard.instance-index` | `0` | `AuditProperties.Rabbit.Shard` | This instance's own index within the configured sharding topology, consumed by the default `ConsistentHashPartitionShardResolver`. Combined with `total-instances=1`, the default makes sharding a complete no-op — every partition hashes to instance 0, the only instance that exists. Must be non-negative (`@Min(0)`); a negative value fails fast at startup instead of silently producing wrong shard-ownership answers. Only enforced if a JSR-380 validator is on the *consuming* application's classpath — see `AuditProperties`'s Javadoc. |
| `audit.rabbit.shard.total-instances` | `1` | `AuditProperties.Rabbit.Shard` | The total number of instances sharing partition ownership. Configuration consistency across instances (every real instance agreeing on this value) is the deploying platform's responsibility — this library cannot validate it from inside any single instance. Must be at least 1 (`@Min(1)`); `0` previously reached `ConsistentHashPartitionShardResolver`'s `Math.floorMod` and threw an `ArithmeticException` on the first inbound message instead of failing at startup — closed by this constraint. Only enforced if a JSR-380 validator is on the *consuming* application's classpath — see `AuditProperties`'s Javadoc. |
| `audit.verification.cron` | `0 0 2 * * *` (daily, 02:00) | `AuditProperties.Verification` | The cron expression `ChainVerifierJob.runScheduled()` runs on, wired by `AuditVerificationAutoConfiguration`. |
| `audit.anchor.bucket` | *(none — must be set explicitly)* | `AuditProperties.Anchor` | The S3 bucket anchors are published to. **No default, deliberately**: a wrong default silently anchoring to the wrong bucket is worse than not anchoring at all. Also the property `AuditAnchorAutoConfiguration`'s `@ConditionalOnProperty(prefix = "audit.anchor", name = "bucket")` gates on — anchoring stays entirely inert until this is set. If set but left blank/whitespace-only, fails fast at startup with a validation error instead of a later, unrelated AWS region-resolution error — see `NotBlankIfPresent` (only enforced if a JSR-380 validator, e.g. `spring-boot-starter-validation`, is on the *consuming* application's classpath; see `AuditProperties`'s Javadoc). |
| `audit.anchor.key-prefix` | `anchors/` | `AuditProperties.Anchor` | The key prefix every anchor object is written under (`S3ObjectLockAdapter`), keyed further by partition and timestamp so writes are append-only by construction. |
| `audit.anchor.retention-years` | `7` | `AuditProperties.Anchor` | How many years of S3 Object Lock Compliance-mode retention `S3ObjectLockAdapter` applies to each anchor object. |
| `audit.anchor.publish-cron` | `0 0 3 * * *` (daily, 03:00 — an hour after verification) | `AuditProperties.Anchor` | The cron expression `AnchorPublisherJob.runScheduled()` runs on, wired by `AuditAnchorAutoConfiguration`. |

## Properties that gate auto-configuration but aren't on `AuditProperties`

These are ordinary Spring Boot infrastructure properties (datasource, Mongo URI, Rabbit
connection, actuator/validation activation) — not introduced by this starter, and not listed
above, since they're either standard Spring Boot properties or driven entirely by whether a
dependency (`spring-boot-starter-actuator`, `spring-boot-starter-validation`, Micrometer) is on
the classpath rather than by a dedicated `audit.*` property:

- `AuditMetricsAutoConfiguration` is unconditional at the class level — always provides an
  `AuditMetricsRecorder` bean (Micrometer-backed if `MeterRegistry` is present, a no-op
  otherwise). No property gates it.
- `AuditHealthAutoConfiguration` is gated on `HealthIndicator` being on the classpath
  (`spring-boot-starter-actuator`, which pulls in `spring-boot-health`) and a `ChainVerifierJob`
  bean existing. No property gates it either.
