# Configuration Reference

Every BSM-related configuration property across `bsm-core`, `bsm-spring-boot-starter`, and
`bsm-svc`. Excludes pure CPMS-platform config unrelated to billing (`services.usg-svc`,
`services.auth-svc`, `services.ppm-svc` HTTP client settings, `aws.s3`, `payment.stripe`,
`payment.razorpay`, `messaging.invoice`, `payment.webhook`, `libs/java-common`'s
`cpms.security.*`) — those are external-integration/platform plumbing, not BSM policy.

`bsm-core` itself defines **zero** Spring configuration — confirmed by
`grep -r "@ConfigurationProperties\|@Value" bsm-core/src/main/java` returning no results. This is
expected and by design: `bsm-core` is framework-agnostic (see `ARCHITECTURE_CERTIFICATION.md`).
All configuration below lives in `bsm-spring-boot-starter` (for starter consumers) or `bsm-svc`
(the real host application).

## Two parallel property surfaces — important

`bsm-spring-boot-starter` consumers and the `bsm-svc` application itself use **different property
paths** for the same underlying policy values, because they bind through different classes:

- **Starter consumers** (anyone depending on `bsm-spring-boot-starter`) set `bsm.dunning.*`,
  `bsm.trial.default-days`, `bsm.reconciliation.*` — bound by
  `bsm-spring-boot-starter/src/main/java/com/company/bsmsvc/starter/config/BsmProperties.java`,
  bridged to `bsm-core`'s `DunningPolicy`/`TrialPolicy`/`ReconciliationPolicy` by
  `BsmPolicyAutoConfiguration`. This is what `STARTER_GUIDE.md`'s property table documents.
- **`bsm-svc` itself** (the real host app, not a starter consumer of its own policy beans) binds
  the same policy shapes through **separate** classes: `dunning.policy.*` via
  `DunningProperties`, `payment.reconciliation.*` via `ReconciliationProperties`, and
  `bsm.trial.default-days` via a direct `@Value` (not a `@ConfigurationProperties` class) — all
  bridged into the same `bsm-core` policy value objects by
  `bsm-svc/src/main/java/com/company/bsmsvc/config/PolicyBeansConfig.java`.

Both tables are below. If you're building on `bsm-spring-boot-starter`, only the `bsm.*` table
applies to you.

---

## `bsm-spring-boot-starter` property reference (`bsm.*`, prefix bound by `BsmProperties`)

Source: `bsm-spring-boot-starter/src/main/java/com/company/bsmsvc/starter/config/BsmProperties.java`.
All fields have Java-level defaults (no external yaml required) and are optional.

| Property | Default | Required/Optional | Owning module | Description |
|---|---|---|---|---|
| `bsm.enabled` | `true` | Optional | bsm-spring-boot-starter | Master switch. `false` disables every `@AutoConfiguration` class in the starter (all ten are gated `@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)`). |
| `bsm.dunning.day1-retry-after-hours` | `24` | Optional | bsm-spring-boot-starter | Hours after first payment failure before dunning retry 1. |
| `bsm.dunning.day3-retry-after-hours` | `72` | Optional | bsm-spring-boot-starter | Hours before dunning retry 2. |
| `bsm.dunning.day7-retry-after-hours` | `168` | Optional | bsm-spring-boot-starter | Hours before dunning retry 3. |
| `bsm.dunning.suspend-after-days` | `14` | Optional | bsm-spring-boot-starter | Days in dunning before the subscription is suspended. |
| `bsm.dunning.cancel-after-days` | `30` | Optional | bsm-spring-boot-starter | Days in dunning before the subscription is cancelled. |
| `bsm.trial.default-days` | `14` | Optional | bsm-spring-boot-starter | Trial length granted by `TenantOnboardingService` when the resolved plan is the trial plan. |
| `bsm.reconciliation.threshold-seconds` | `300` | Optional | bsm-spring-boot-starter | Age (seconds) at which a `PENDING` payment is considered stale for reconciliation. |

All ten bind via `@ConfigurationProperties(prefix = "bsm")` root class `BsmProperties`, with
nested static classes `Dunning`, `Trial`, `Reconciliation` — every field has a Java default, so
none of these are hard-required to be set in application config; the starter works out of the box
with these defaults. `BsmPolicyAutoConfiguration` does `@EnableConfigurationProperties(BsmProperties.class)`
and constructs `bsm-core`'s `DunningPolicy`/`TrialPolicy`/`ReconciliationPolicy` beans from it.

**`@ConditionalOnProperty` gating** — every one of the ten `@AutoConfiguration` classes in
`bsm-spring-boot-starter/src/main/java/com/company/bsmsvc/starter/config/` (`BsmPolicyAutoConfiguration`,
`BsmSupportAutoConfiguration`, `TenantBillingProfileAutoConfiguration`, `PaymentAutoConfiguration`,
`SubscriptionAutoConfiguration`, `InvoiceRenewalAutoConfiguration`, `DunningAutoConfiguration`,
`TenantOnboardingAutoConfiguration`, `InvoiceAutoConfiguration`,
`BsmPortAvailabilityValidatorAutoConfiguration`) checks
`@ConditionalOnProperty(prefix = "bsm", name = "enabled", matchIfMissing = true)` — i.e. `bsm.enabled`
defaults to effectively-true when absent.

Overriding any policy bean directly (rather than via properties) also works — see
`STARTER_GUIDE.md`'s "Overriding a default bean" section
(`customDunningPolicyBean_takesPrecedenceOverPropertiesBinding`).

---

## `bsm-svc` property reference (the real host application's own binding)

### Dunning policy — prefix `dunning`

Source: `bsm-svc/src/main/java/com/company/bsmsvc/config/DunningProperties.java` —
`@ConfigurationProperties(prefix = "dunning")`, a Java `record` with **no field-initializer
defaults** (`record DunningProperties(Policy policy)`, nested `record Policy(int
day1RetryAfterHours, int day3RetryAfterHours, int day7RetryAfterHours, int suspendAfterDays, int
cancelAfterDays)`). Because Java records give primitives no implicit default, these are
**required to bind** unless supplied by `application.yaml` — which `bsm-svc` does ship with
defaults via environment-variable placeholders:

| Property | yaml-shipped default (env var) | Required/Optional | Owning module | Description |
|---|---|---|---|---|
| `dunning.policy.day1-retry-after-hours` | `24` (`DUNNING_DAY1_HOURS`) | Required (record has no code default; relies on `application.yaml`) | bsm-svc | Hours after first payment failure before retry 1. |
| `dunning.policy.day3-retry-after-hours` | `72` (`DUNNING_DAY3_HOURS`) | Required | bsm-svc | Hours before retry 2. |
| `dunning.policy.day7-retry-after-hours` | `168` (`DUNNING_DAY7_HOURS`) | Required | bsm-svc | Hours before retry 3. |
| `dunning.policy.suspend-after-days` | `14` (`DUNNING_SUSPEND_DAYS`) | Required | bsm-svc | Days in dunning before suspension. |
| `dunning.policy.cancel-after-days` | `30` (`DUNNING_CANCEL_DAYS`) | Required | bsm-svc | Days in dunning before cancellation. |
| `dunning.scheduler.retry-interval-ms` | `300000` (`DUNNING_RETRY_INTERVAL_MS`) | Optional (code default `300000` in `DunningScheduler.java:16` matches yaml) | bsm-svc | How often the dunning scheduler polls for due retries. Not part of `DunningProperties` — a separate `@Value` on `DunningScheduler`. |

`bsm-svc/src/main/java/com/company/bsmsvc/config/PolicyBeansConfig.java` reads
`DunningProperties.policy()`'s five fields directly into `bsm-core`'s `DunningPolicy` value
object — a 1:1 field mapping with the starter's `bsm.dunning.*` shape above.

### Reconciliation policy — prefix `payment.reconciliation`

Source: `bsm-svc/src/main/java/com/company/bsmsvc/config/ReconciliationProperties.java` —
`@ConfigurationProperties(prefix = "payment.reconciliation")`, record `(int thresholdSeconds, long
intervalMs)`, also with no code-level defaults.

| Property | yaml-shipped default (env var) | Required/Optional | Owning module | Description |
|---|---|---|---|---|
| `payment.reconciliation.threshold-seconds` | `300` (`PAYMENT_RECONCILIATION_THRESHOLD_SECONDS`) | Required (no code default) | bsm-svc | Age (seconds) at which a `PENDING` payment is considered stale for reconciliation. **Only field actually consumed** by `PolicyBeansConfig`'s `reconciliationPolicy` bean. |
| `payment.reconciliation.interval-ms` | `300000` (`PAYMENT_RECONCILIATION_INTERVAL_MS`) | Required (no code default) | bsm-svc | How often the reconciliation scheduler runs. **Present on `ReconciliationProperties` but not read by `PolicyBeansConfig`** — bound and available, but currently unused in the policy bridge; worth flagging as a possible dead/reserved field or a scheduler wiring gap. |

### Trial policy — prefix `bsm.trial` (direct `@Value`, not a `@ConfigurationProperties` class)

| Property | yaml-shipped default (env var) | Required/Optional | Owning module | Description |
|---|---|---|---|---|
| `bsm.trial.default-days` | `14` (`BSM_TRIAL_DEFAULT_DAYS`) | Optional — `@Value("${bsm.trial.default-days:14}")` at `PolicyBeansConfig.java:34`, code-level default of `14` matches the yaml default | bsm-svc | Trial length granted by `TenantOnboardingService`. Unlike dunning/reconciliation, this one field is bound via a plain `@Value` placeholder directly in `PolicyBeansConfig`, not through its own `@ConfigurationProperties` class. |

### Other `bsm.*`-prefixed `bsm-svc` operational properties (schedulers/outbox/messaging)

Found via a broader repo grep of `@Value` placeholders in `bsm-svc/src/main/java/com/company/bsmsvc/application/scheduler/*`
and `infrastructure/outbox/*` — BSM-relevant, and flagged here because their **code-level
`@Value` defaults disagree with the shipped `application.yaml` defaults** (yaml wins in practice
since it's present in the shipped resource file):

| Property | yaml default (env var) | Code-level `@Value` default | Match? | Owning module | Description |
|---|---|---|---|---|---|
| `bsm.internal-secret` | `${INTERNAL_SERVICE_SECRET:change-me-in-production}` | — | Required in any real deployment; insecure placeholder default | bsm-svc | Shared secret for internal service-to-service calls. **Ships with an insecure default** — must be overridden outside local dev. |
| `bsm.schedule.executor-interval-ms` | `10000` (`BSM_SCHEDULE_EXECUTOR_INTERVAL_MS`) | `300000` (`SubscriptionScheduleExecutorScheduler.java:23`) | **Mismatch** — yaml value (10s) wins whenever `application.yaml` is loaded; the Java-level default (300s) only applies if the yaml file/property is absent entirely | bsm-svc | Poll interval for executing due `SubscriptionSchedule` actions (e.g. scheduled downgrades). |
| `bsm.provider-sync.retry-interval-ms` | `60000` (`BSM_PROVIDER_SYNC_RETRY_INTERVAL_MS`) | `600000` (`ProviderSyncRetryScheduler.java:31`) | **Mismatch**, same pattern as above | bsm-svc | Retry interval for subscriptions pending provider (Stripe/Razorpay) sync. |
| `bsm.outbox.poll-interval-ms` | `5000` (`BSM_OUTBOX_POLL_INTERVAL_MS`) | `5000` (`BsmOutboxPollerScheduler.java:14`) | Match | bsm-svc | Outbox poller interval. |
| `bsm.outbox.batch-size` | `50` (`BSM_OUTBOX_BATCH_SIZE`) | `50` (`BsmOutboxPublisher.java:43`) | Match | bsm-svc | Outbox publish batch size. |
| `bsm.messaging.tenant-created-queue` | `q.bsm.tenant-created` (`BSM_TENANT_CREATED_QUEUE`) | `q.bsm.tenant-created` (`TenantCreatedConsumer.java:22`) | Match | bsm-svc | RabbitMQ queue name for inbound tenant-created events. |

The two mismatches above (`bsm.schedule.executor-interval-ms`, `bsm.provider-sync.retry-interval-ms`)
are worth a closer look before relying on either number in isolation — since `application.yaml`
ships with the repo, the yaml value is what actually takes effect in `bsm-svc` as deployed today;
the differing `@Value` fallback would only matter if that yaml key were ever removed.

There's also a commented-out, all-zeros `dunning:` override block at
`bsm-svc/src/main/resources/application.yaml:288-297` ("fires immediately") — looks like a
leftover local-testing override, not active configuration; flagged here in case it's mistaken for
a real profile.

---

## All `@ConfigurationProperties` classes found repo-wide (context, not all BSM-scoped)

Confirmed exhaustive via repo-wide grep. Only the first four rows are BSM-scoped and covered
above; the rest are external-integration plumbing explicitly out of scope per this doc's opening
note, listed here only so it's clear they were checked and deliberately excluded.

| Class | Prefix | BSM-scoped |
|---|---|---|
| `bsm-spring-boot-starter/.../starter/config/BsmProperties.java` | `bsm` | Yes — covered above |
| `bsm-svc/.../config/BsmSecurityProperties.java` | `bsm` | Yes, but security/internal-secret plumbing, not a billing policy — see `bsm.internal-secret` row above |
| `bsm-svc/.../config/DunningProperties.java` | `dunning` | Yes — covered above |
| `bsm-svc/.../config/ReconciliationProperties.java` | `payment.reconciliation` | Yes — covered above |
| `bsm-svc/.../config/UsgProperties.java` | `services.usg-svc` | No — external USG-SVC client config |
| `bsm-svc/.../config/AuthProperties.java` | `services.auth-svc` | No — external AUTH-SVC client config |
| `bsm-svc/.../config/PpmProperties.java` | `services.ppm-svc` | No — external PPM-SVC client config |
| `bsm-svc/.../config/AwsS3Properties.java` | `aws.s3` | No — invoice PDF storage backend config, adjacent but not billing policy |
| `bsm-svc/.../config/StripeProperties.java` | `payment.stripe` | No — gateway credentials/config |
| `bsm-svc/.../config/RazorpayProperties.java` | `payment.razorpay` | No — gateway credentials/config |
| `bsm-svc/.../config/MessagingProperties.java` | `messaging.invoice` | No — RabbitMQ topology config |
| `bsm-svc/.../config/WebhookProperties.java` | `payment.webhook` | No — webhook signature/verification config |
| `libs/java-common/.../CpmsSecurityProperties.java` | `cpms.security` | No — shared platform lib, unrelated to BSM |

---

## Summary

- **17 BSM-scoped properties documented**: 7 in the starter's `bsm.*` surface (all optional, all
  with Java defaults), 5 in `bsm-svc`'s `dunning.policy.*` (yaml-required), 2 in `bsm-svc`'s
  `payment.reconciliation.*` (yaml-required, one of which is unused by `PolicyBeansConfig`), 1
  `bsm.trial.default-days` `@Value`, plus 6 additional `bsm.*`-prefixed `bsm-svc` operational
  scheduler/outbox/messaging properties (2 with a code-vs-yaml default mismatch, 1 shipping an
  insecure default).
- **Discrepancy vs. `STARTER_GUIDE.md`**: none in the core `bsm.dunning`/`bsm.trial`/`bsm.reconciliation`
  table — it matches `BsmProperties.java` exactly. The nuance `STARTER_GUIDE.md` doesn't mention is
  that `bsm-svc` itself binds the *same policy values* through an entirely separate property
  surface (`dunning.policy.*` / `payment.reconciliation.*`) — worth a cross-reference note in
  `STARTER_GUIDE.md` for anyone reading both docs side by side.
- **New finding, not in any existing doc**: `ReconciliationProperties.intervalMs` is bound but
  never read by `PolicyBeansConfig`, and `bsm.schedule.executor-interval-ms` /
  `bsm.provider-sync.retry-interval-ms` have code-level `@Value` fallback defaults that disagree
  with the shipped `application.yaml` defaults (yaml wins today, but the discrepancy is latent).
