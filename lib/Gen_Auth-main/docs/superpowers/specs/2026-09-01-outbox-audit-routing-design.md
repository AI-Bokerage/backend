# Design: Transactional Outbox + Two-Tier Audit Routing

Third sub-project of the CPMS-parity effort (`gap.md` §3 "Event delivery reliability gap" and §6
"Two-tier audit logging" — covered together per gap.md's own note that §6 "can build on §3's
outbox/exchange work"; confirmed by reading CPMS-Platform's real code that these are in fact one
mechanism, not two).

**Compatibility source of truth**: `CPMS-Platform/apps/auth-svc/src/main/java/com/example/authsvc/infrastructure/messaging/outbox/AuthOutboxRelayJob.java`,
its `AuthEventPublisher`, `common/audit/AuthAuditEventRouter.java`, and the `auth_outbox_events`
Flyway migration (`V3__auth_outbox_events.sql`) — all read directly during brainstorming.
gen-auth-starter's current `AuthEventPublisher`/`AuditLogService`/`MessagingConfig` were also read
fresh (not assumed from memory).

## Scope

- **In scope**: a new `auth_outbox_events` table + relay job replacing gen-auth-starter's current
  fire-and-forget `AuthEventPublisher` internals with transactional outbox writes; retry/failure
  handling matching CPMS's actual (modest) guarantees; two-tier audit-exchange classification
  riding the same table via its `target_exchange` column.
- **Out of scope**: any change to the existing `auth_audit_logs` DB table or `AuditLogService`
  (stays exactly as-is — additive, not replacing); consumer-side dedup (CPMS itself doesn't do
  this either — see Data flow); CPMS-specific exchange names (`cpms.audit`, `cpms.platform.audit`,
  `cpms.events`) — these become configurable properties, not hardcoded.

## Components

### Schema — `V7__auth_outbox_events.sql`

```sql
CREATE TABLE auth_outbox_events (
    id              UUID            NOT NULL,
    event_id        UUID            NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    user_id         UUID,
    target_exchange VARCHAR(255),
    status          VARCHAR(50)     NOT NULL DEFAULT 'PENDING',
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ,
    last_error      TEXT,
    version         BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_auth_outbox_events   PRIMARY KEY (id),
    CONSTRAINT uq_auth_outbox_event_id UNIQUE      (event_id)
);
CREATE INDEX idx_auth_outbox_status_created_at ON auth_outbox_events (status, created_at);
```

Verbatim from CPMS's `V3__auth_outbox_events.sql` (renumbered to `V7` — gen-auth-starter's latest
is `V6__oauth.sql`). `target_exchange = NULL` means "publish to the default business exchange"
(`app.messaging.exchange`, already existing, default `auth.events`); non-null overrides to a
specific exchange (used by audit-tier rows — see below).

### `AuthOutboxEventEntity` + `AuthOutboxEventJpaRepository` (new)

JPA mapping for the table above. Repository gets one native query, copied from CPMS:

```java
@Query(value = "SELECT * FROM auth_outbox_events WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED",
       nativeQuery = true)
List<AuthOutboxEventEntity> findPendingForUpdate(@Param("limit") int limit);
```

Plus three small mutation methods used by the relay job: `markPublished(UUID id, Instant publishedAt)`,
`incrementRetry(UUID id, String error)`, `markFailed(UUID id, String error)` — each a `@Modifying
@Query` update, matching this codebase's existing `AuthSessionJpaRepository` bulk-update style
(`deactivateAllByUserId` etc. — same pattern, not a new convention).

### `AuthEventPublisher` — internals replaced, signatures unchanged

Current file: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging/producer/AuthEventPublisher.java`.
Every existing public method keeps its exact signature (all 5 existing call sites — `ChangePasswordServiceImpl`,
`LoginExecutionServiceImpl` ×2, `RefreshTokenServiceImpl` ×4, `ImpersonationTokenServiceImpl` — are
untouched). Internally, the private `publish(...)` helper that currently calls
`rabbitTemplate.convertAndSend(...)` directly is replaced with `enqueue(routingKey, payload,
userId, targetExchange)`, which:

1. Serializes `payload` to JSON via the existing `ObjectMapper`. **Serialization failure is
   rethrown** (`IllegalStateException`), not swallowed — matches CPMS: since every call site runs
   inside an ambient `@Transactional` business method, a bad payload rolls back that transaction
   rather than silently losing the event.
2. Builds an `AuthOutboxEventEntity` (`id = UUID.randomUUID()`, `eventId = UUID.randomUUID()`,
   `eventType = routingKey`, `payload = <json>`, `userId`, `targetExchange`, `status = "PENDING"`,
   `createdAt = Instant.now()`) and saves it via `AuthOutboxEventJpaRepository`. No new
   `@Transactional` boundary is added to `AuthEventPublisher` itself — `enqueue(...)` runs inside
   whatever transaction context its caller is already in. For the call sites that publish
   synchronously (`ChangePasswordServiceImpl`, `ImpersonationTokenServiceImpl`, and the new
   audit-tier methods once wired in — see below), the outbox write commits atomically with the
   business state change that triggered it. **`LoginExecutionServiceImpl`'s login-success/failed
   publishes are the exception**: they already run inside a detached `asyncExecutor.execute(...)`
   fire-and-forget block in the current code (same pattern its existing `auditLogService.log(...)`
   call uses), with no ambient transaction — this design does not change that. Their outbox row
   still gets written (via `JpaRepository`'s own per-call transaction), just not atomically with
   the login itself, which is consistent with — not a regression from — today's behavior, where
   the same async detachment already separates the RabbitMQ publish from the login transaction.

`publishImpersonationEnded` (zero call sites today, confirmed) is deleted — matches CPMS's own
choice for the identical dead method, and this codebase's YAGNI convention.

`RabbitTemplate` itself is no longer injected into `AuthEventPublisher` at all — only
`AuthOutboxEventJpaRepository` and the `ObjectMapper`. The relay job below is the only component
that talks to RabbitMQ.

### Audit-tier methods (new, same class)

Six new typed methods, matching CPMS's split (tenant vs. platform, chosen by the caller — never
inferred):

```java
public void publishAuditTenantLoginSuccess(UUID userId, UUID tenantId, Instant occurredAt)
public void publishAuditTenantLoginFailed(UUID tenantId, Instant occurredAt, String reason)
public void publishAuditPlatformLoginSuccess(UUID userId, Instant occurredAt)
public void publishAuditPlatformLoginFailed(Instant occurredAt, String reason)
public void publishAuditLogout(UUID userId, UUID tenantId, Instant occurredAt)
public void publishAuditPasswordChanged(UUID userId, UUID tenantId, Instant occurredAt)
```

**Implementation note (post-implementation correction):** the design originally called for a
private `enqueueAudit(routingKey, payload, userId, tier)` helper keyed on an `AuditTier { TENANT,
PLATFORM, BOTH }` enum, with `BOTH` reserved for a future critical-security-event case (matching
CPMS's `token.replay_detected` precedent, which writes two rows — one per exchange). The
implementation correctly dropped this indirection: none of the six methods above ever needs
`BOTH` (gen-auth-starter's current event set has no such case), so each method just calls the
existing `enqueue(routingKey, payload, userId, targetExchange)` directly with its own tier's
resolved exchange name from `AuditRoutingProperties` (see below) — no enum, no extra helper. This
is the correct YAGNI call: an enum whose third constant has no caller is exactly the speculative
abstraction not worth having. If a real `BOTH`-shaped case ever arises, add the two-row helper
then, informed by an actual caller instead of a hypothetical one.

These are net-new methods with no existing call sites — wiring them into `LoginExecutionServiceImpl`
etc. (which already knows tenant-vs-platform from `AuthUserEntity#getUserType()`) is part of this
sub-project's implementation, not a future gap.

### `AuthOutboxRelayJob` (new)

```java
@Scheduled(fixedDelayString = "${app.messaging.outbox.relay-interval-ms:5000}")
@Transactional
public void relay() {
    if (rabbitTemplate == null) { return; } // no broker configured — silent no-op, matches AuthEventPublisher's existing posture
    List<AuthOutboxEventEntity> batch = repository.findPendingForUpdate(batchSize);
    for (AuthOutboxEventEntity event : batch) {
        try {
            String exchange = event.getTargetExchange() != null ? event.getTargetExchange() : defaultExchange;
            Message message = MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(event.getEventId().toString())
                    .build();
            rabbitTemplate.send(exchange, event.getEventType(), message);
            repository.markPublished(event.getId(), Instant.now());
        } catch (Exception e) {
            if (event.getRetryCount() >= MAX_RETRIES - 1) {
                repository.markFailed(event.getId(), e.getMessage());
            } else {
                repository.incrementRetry(event.getId(), e.getMessage());
            }
        }
    }
}
```

`MAX_RETRIES = 3` (constant, matches CPMS — not a property, since CPMS itself hardcodes it and
there's no indication host apps need to tune it). `batchSize` via
`@Value("${app.messaging.outbox.batch-size:50}")`. No exponential backoff beyond the 5s poll
interval — matches CPMS's actual (modest) behavior exactly, not a gold-plated version. Whole
method `@Transactional` — the `SELECT ... FOR UPDATE SKIP LOCKED` lock, the send, and the
status-mutation all happen in one transaction per batch, same as CPMS.

`AuthOutboxRelayJob` is gated the same way as the rest of messaging:
`@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")` — it
doesn't exist at all unless messaging is on, matching `AuthEventPublisher`'s and `MessagingConfig`'s
existing gate. `@EnableScheduling` is added directly to `MessagingConfig` itself (this codebase's
established convention for enabling a cross-cutting Spring feature from inside the library: compare
`SecurityConfig`, which carries `@EnableWebSecurity` and `@EnableMethodSecurity` directly on the
class rather than requiring the host app's main class to declare them) — this is the **first**
`@Scheduled` job in gen-auth-starter, so no existing `@EnableScheduling` is present anywhere to
conflict with.

### `AuditRoutingProperties` (new, `@ConfigurationProperties(prefix = "app.messaging.audit")`)

```java
@Data
@ConfigurationProperties(prefix = "app.messaging.audit")
public class AuditRoutingProperties {
    private String tenantExchange = "auth.audit.tenant";
    private String platformExchange = "auth.audit.platform";
}
```

Generic default names (not `cpms.audit`/`cpms.platform.audit` — those are CPMS's own naming,
this is the one place the design must not hardcode them). A host app wanting exact CPMS parity
sets `app.messaging.audit.tenant-exchange=cpms.audit` /
`app.messaging.audit.platform-exchange=cpms.platform.audit` in its own config — no code change
needed, matching how `MessagingProperties.exchange` already works for the business exchange today.

### `MessagingConfig` addition

Two new exchange beans registered from `AuditRoutingProperties`, alongside the existing single
business-exchange bean, matching CPMS's exact exchange types: the tenant-audit exchange is a
`TopicExchange` (routing-key-based, same type as the existing business exchange), the
platform-audit exchange is a `FanoutExchange` (CPMS's real choice — a single global
platform-tier audit stream has no routing-key structure worth preserving, broadcast is simpler).
Both new beans only exist when `app.messaging.enabled=true` (same class-level gate).

## Data flow

1. A business method (e.g. `ChangePasswordServiceImpl.changePassword(...)`, called synchronously
   inside its own transaction) calls `authEventPublisher.publishPasswordChanged(...)` — the outbox
   row commits atomically with the password change itself. `LoginExecutionServiceImpl` is the one
   exception (see the note under `AuthEventPublisher` above): its
   `publishLoginSuccess(...)`/new `publishAuditTenantLoginSuccess(...)` calls run inside an
   existing detached async block, so their outbox rows land shortly after the login transaction
   commits, not atomically with it — unchanged from today's behavior.
2. `AuthOutboxRelayJob` wakes every 5s, locks up to 50 `PENDING` rows with `SKIP LOCKED`, sends
   each to its resolved exchange (row's `target_exchange` or the default business exchange),
   marks `PUBLISHED` or increments retry/marks `FAILED`.
3. Downstream consumers (a separate audit-consumer service, in CPMS's case) subscribe to whichever
   exchanges they care about — gen-auth-starter, per its existing convention, declares only
   exchanges, no queues or bindings ("consuming applications own their own topology").

## Error handling

- Serialization failure at enqueue time: rethrown, rolls back the caller's transaction — an event
  that can't even be serialized never gets a row, and the triggering business action itself fails
  loudly rather than silently proceeding with a lost event.
- Send failure at relay time: retried up to 3 total attempts, then `FAILED` — a `FAILED` row is
  not automatically retried again; matches CPMS (no dead-letter queue, no alerting mechanism
  specified — out of scope, same as CPMS's own actual scope).
- No broker configured (`RabbitTemplate` absent): relay job no-ops silently every tick, matching
  `AuthEventPublisher`'s existing "swallow and log" posture for the pre-outbox fire-and-forget path
  — the difference now is nothing is lost, rows just accumulate as `PENDING` until a broker exists.
- Dedup: `event_id` is unique at the DB level and threaded onto the AMQP message as `messageId` —
  this is a stable id for **consumer-side** dedup, not an active dedup mechanism in gen-auth-starter
  itself. This matches CPMS exactly: CPMS does no consumer-side or broker-side dedup either, it's
  an explicit "dedup is the consumer's job" contract, not a gap introduced by this design.

## Testing

- Unit: `AuthEventPublisher` — each existing typed method still produces the same call shape
  (no behavior change from a caller's perspective) but now writes an outbox row instead of calling
  `RabbitTemplate` directly (verify via `AuthOutboxEventJpaRepository` mock, `ArgumentCaptor` on
  the saved entity's `eventType`/`payload`/`targetExchange`). New audit methods each verified to
  produce the correct `targetExchange` per tier (TENANT/PLATFORM), and a hypothetical BOTH-tier
  path (test the mechanism even with no real caller yet) produces exactly 2 rows.
- Unit: `AuthOutboxRelayJob` — a `PENDING` row sends successfully → `markPublished` called, message
  has `messageId = eventId`, exchange resolution (null → default, non-null → override) both
  covered; a send that throws on attempt 1-2 → `incrementRetry`; a send that throws on attempt 3
  (`retryCount == MAX_RETRIES - 1` going in) → `markFailed`; `rabbitTemplate == null` → repository
  never queried, no-op.
- Integration (Testcontainers, matching this codebase's existing Postgres-integration-test
  convention): apply `V7` migration, insert a `PENDING` row directly, run `relay()`, confirm status
  transitions and that `findPendingForUpdate` genuinely skips locked rows under concurrent access
  (two threads/transactions calling it simultaneously each get disjoint batches) — this is the one
  claim in the whole design that a mocked unit test cannot prove.
- Config test (`ApplicationContextRunner`, matching `OtpConfigTest`/`OAuthConfigTest` style):
  `AuthOutboxRelayJob`/audit exchange beans absent when `app.messaging.enabled=false`, present
  when `true`; `AuditRoutingProperties` defaults resolve to `auth.audit.tenant`/`auth.audit.platform`
  when unset, override cleanly when a host app sets `cpms.audit`/`cpms.platform.audit`.
