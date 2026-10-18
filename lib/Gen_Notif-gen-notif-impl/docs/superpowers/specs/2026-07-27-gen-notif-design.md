# Gen_NOTIF — Notification Delivery — Design

## Goal

Build Gen_NOTIF: the sixth Gen_MS library, owning notification delivery
across email, webhook, in-app (real-time), and SMS channels, with
per-user preferences, a host-registered template system, and a
self-hosted Socket.IO + Redis real-time gateway. TypeScript/Express/
Prisma, matching Gen_REG/Gen_TBR conventions.

## Why

Per `CPMS-Platform/PMP_Service_Reuse_Decision_Matrix.md`'s NOTIF row:
`notif-svc` is **ADAPT** grade (email/webhook/in-app/SMS dispatch,
preferences, template registry, digest batching — real logic, needs a
Mongo→Postgres re-platform) and `com-svc`'s Socket.io/Redis-adapter layer
is called out as "near-DIRECT-REUSE grade" for the real-time transport,
even though the rest of `com-svc` (chat/messaging) is out of scope. This
spec is written directly from a full read of both services' notification-
relevant source (see the research findings this design is based on:
`notif-svc`'s dispatch pipeline, channels, Prisma+Mongo schema, workers,
ADRs 0001-0006; `com-svc`'s `ws/server.ts`, `ws/rooms.ts`,
`realtime/notif-subscriber.ts`, JWT handshake auth) — not from a blank
boundary like Gen_TBR was. Unlike Gen_TBR, Gen_NOTIF has real
`pre-context/` source with real bugs to fix, not just a design boundary
to fill in.

**Explicit scope call (confirmed with the user before this spec was
written):** Gen_NOTIF covers **notification delivery only**. `com-svc` is
actually a full team-chat backend (conversations, threads, mentions,
reactions, pins, starred messages, KMS-encrypted attachments, audit) —
none of that ports into Gen_NOTIF. Only its real-time transport plumbing
(Socket.IO + Redis adapter + room/JWT-handshake pattern) is relevant, and
only because Gen_NOTIF needs the same "push a message from a backend
event into a browser tab" primitive that chat also happens to need.

## Scope

**In scope (v1):**

- **Four delivery channels**, each behind a swappable port:
  - **Email** — `IEmailSender` port, default `NodemailerEmailSender`
    (plain SMTP transport; SES's own SMTP credentials work through this
    with zero special-casing — no separate "SES tier" vs "generic SMTP
    tier" branching like the source had).
  - **Webhook** — HMAC-SHA256 signed (`X-Gen-Notif-Signature: sha256=<hex>`
    + `X-Gen-Notif-Timestamp: <unix>`, signed data `${timestamp}.${body}`,
    ±300s replay grace window), bounded in-process retry
    (`[0, 1000, 4000]`ms, 3 attempts), fanned out in parallel to every
    enabled endpoint a tenant/user has registered.
  - **In-app** — real-time delivery via a self-hosted Socket.IO server +
    Redis adapter, all owned by Gen_NOTIF itself (see Architecture).
  - **SMS** — `ISmsSender` port with **no working default adapter**.
    Calling it without a configured override throws
    `SmsNotConfiguredError` — never a silent fake-success log (the
    source's actual behavior: it logged a permanent `FAILED` notification
    record and returned normally, which looks like success to any caller
    not reading logs).
- **Host-registered template system** — `registerTemplate(eventType,
  builder)` at config time. Gen_NOTIF ships the registry mechanism and one
  fallback builder, zero hardcoded content. `NotificationContent {title,
  body, html?}` is channel-neutral; only email renders `html`.
- **Per-user preferences** — one row per `(tenantId, userId, eventType,
  channel)`: `enabled` + `digestMode`. Postgres/Prisma, RLS with `FORCE`
  and `WITH CHECK` from the very first migration.
- **Notification log** — one row per delivery attempt, real `status`
  (`SENT`/`FAILED`/`QUEUED_FOR_DIGEST`/`READ`) reflecting the actual
  outcome of that specific channel attempt. Unread-list + mark-read API.
- **Digest batching** — accumulate a user's digest-mode notifications into
  one pending batch row; a host-triggered `runDigestSweep()` drains due
  batches into one real, content-bearing email (not just a count).
- **Realtime gateway** — `IRealtimeGateway` port, default
  `SocketIoRedisGateway`: owns the Socket.IO server setup, the Redis
  adapter wiring, the JWT handshake auth, the room-join convention, and
  the Redis-pub/sub-to-socket bridge, all in one place.
- **Direct trigger API** — `notify({tenantId, recipients, eventType, data,
  entityRef?})`, callable in-process or via `POST /internal/notify`. No
  message broker required.
- **Optional per-channel gate hook** — `channelGate?(tenantId, channel)`,
  default always-allow, replacing the source's FMM-specific entitlement
  check with a generic, host-suppliable predicate.

**Out of scope (v1 — each with its own rationale):**

- **Full chat/messaging domain.** `com-svc`'s conversations/threads/
  mentions/reactions/pins/starred/encrypted-attachments/audit are a
  distinct product concern (team chat), not notification delivery.
  Confirmed explicitly with the user; not ported at all.
- **RabbitMQ, outbox, and saga durability machinery.** The source's
  outbox only durability-guards one observability event
  (`notif.notification.sent`) — actual channel dispatch is synchronous
  and best-effort either way. Keeping Gen_NOTIF's footprint small (same
  precedent Gen_TBR set: no Redis/worker infra beyond what a feature
  strictly needs) means `notify()` is a direct async call; its durability
  is "as durable as the caller's own retry policy," documented plainly
  rather than built as infrastructure. A future queue-backed trigger is a
  Phase 2 question, not an assumption baked in now.
- **MongoDB.** All persistence — including the 5 collections the source
  kept in Mongo with zero tenant-isolation enforcement — moves to
  Postgres/Prisma with RLS. This migration *is* the redesign, not a
  follow-up task.
- **SSE / RBAC-permission-sync.** Confirmed by source inspection: the
  source's `sse/*` machinery is an unrelated permission-change-sync
  channel, not the in-app notification delivery path (that's the
  Redis-pub/sub-to-Socket.IO bridge instead). Not ported.
- **A working SMS provider.** Port only; bring-your-own Twilio/SNS/etc.
- **Tenant-existence validation against Gen_TNT.** Same rationale as
  Gen_TBR: the caller's `tenantId` is trusted outright (validated only as
  a UUID shape); authorization is the host's job.
- **FMM-style plan/entitlement gating.** CPMS-specific policy; replaced
  by the generic optional `channelGate` hook.
- **An internal scheduler/cron.** `runDigestSweep()` is host-triggered
  (the host wires it to their own cron/interval) — Gen_NOTIF ships no
  `node-cron` or background process in v1, matching Gen_TBR's "no
  background worker in v1" precedent.

## Architecture

### Cross-library integration

Even less coupled than Gen_TBR: no port to any sibling service at all (no
`ITntClient`-equivalent). JWT verification for the realtime gateway's
handshake is behind its own swappable `IJwtVerifier` port (default: JWKS
via `jose`'s `createRemoteJWKSet`, configured by `JWKS_URL`/`JWT_ISSUER`
env vars) — this happens to interoperate with Gen_Auth's JWKS endpoint if
a host points it there, but Gen_NOTIF has no code-level knowledge of
Gen_Auth's existence.

### The factory pattern

```ts
export interface GenNotifConfig {
  preferenceRepo?: ITenantPreferenceRepo;
  notificationRepo?: INotificationLogRepo;
  digestRepo?: IDigestQueueRepo;
  webhookRepo?: IWebhookEndpointRepo;
  emailSender?: IEmailSender;       // override-or-default -> NodemailerEmailSender
  smsSender?: ISmsSender;           // override-or-default -> UnimplementedSmsSender
  realtimeGateway?: IRealtimeGateway; // override-or-default -> SocketIoRedisGateway
  jwtVerifier?: IJwtVerifier;       // override-or-default -> JwksJwtVerifier
  channelGate?: (tenantId: string, channel: NotifChannel) => boolean | Promise<boolean>;
  modules?: GenNotifModulesConfig;
  internalSecret?: string;
}

export interface GenNotifModulesConfig {
  preferences?: boolean;       // default true
  notifications?: boolean;     // default true (unread list / mark-read)
  webhookEndpoints?: boolean;  // default true (CRUD for webhook subscriptions)
  realtime?: boolean;          // default true (exposes attachRealtime)
}

export interface GenNotifInstance {
  app: Express;
  attachRealtime?: (httpServer: HttpServer) => SocketIoServer; // present iff modules.realtime !== false
  notify(input: NotifyInput): Promise<NotifyResult>;
  runDigestSweep(): Promise<DigestSweepResult>;
}
```

Same `resolveXxx(override)` idiom as every sibling: an override is used
as-is; otherwise the default adapter is built lazily from env vars via
`requireEnv`, throwing `GenNotifConfigError` synchronously at
`createGenNotif()` call time.

`attachRealtime` is a deliberate explicit step, not a hidden side effect
of `createGenNotif()` — Socket.IO needs an `http.Server`, not just an
Express `app`, so the demo/host does:

```ts
const genNotif = createGenNotif({});
const server = http.createServer(genNotif.app);
genNotif.attachRealtime?.(server);
server.listen(PORT);
```

This keeps `app` uniform with every other Gen_MS sibling (mountable
in-process or `.listen()`-able standalone) while making the realtime
wiring an opt-in, visible call — consistent with the hexagonal
"everything explicit at construction time" philosophy the whole family
follows.

### Ports

- `ITenantPreferenceRepo` — `findAll(tenantId, userId)`,
  `findByEventType(tenantId, userId, eventType)`, `upsert(input)`.
- `INotificationLogRepo` — `create(input)`, `findUnread(tenantId, userId,
  {limit, before})`, `findById(id)`, `markRead(tenantId, userId, id)`.
- `IDigestQueueRepo` — `enqueue(tenantId, userId, email, notificationId,
  scheduledFor)`, `claimDue(batchLimit)`, `markSent(id)`.
- `IWebhookEndpointRepo` — CRUD for `{tenantId, userId, url, secret,
  enabled, description?}`.
- `IEmailSender` — `send({to, subject, html, text}): Promise<void>`.
- `ISmsSender` — `send({to, body}): Promise<void>`.
- `IRealtimeGateway` — `attach(io: SocketIoServer): void` (wires JWT
  handshake auth, room-join, and the pub/sub bridge onto a Socket.IO
  server instance), `publishInApp(tenantId, userId, payload): Promise<void>`.
- `IJwtVerifier` — `verify(token): Promise<{sub, tenantId, roles}>`.

### Domain model (all Postgres/Prisma — the Mongo→Postgres migration is
part of this design, not deferred)

**`NotificationPreference`** (table `notification_preference`) — PK
`id`, unique on `(tenantId, userId, eventType, channel)`, fields
`enabled` (default true), `digestMode` (default false). RLS: `ENABLE
ROW LEVEL SECURITY`, `FORCE ROW LEVEL SECURITY`, and a policy with both
`USING` and `WITH CHECK` clauses **in the same migration** — the source
shipped `USING`-only, no `FORCE`, and had to patch both gaps in a later
migration after real exposure; Gen_NOTIF ships the fixed version from
day one.

**`NotificationLog`** — `tenantId`, `userId`, `channel` (enum `EMAIL`/
`INAPP`/`SMS`/`WEBHOOK`), `eventType`, `title`, `body`, `entityRefType?`,
`entityRefId?`, `status` (enum `SENT`/`FAILED`/`QUEUED_FOR_DIGEST`/
`READ`), `readAt?`, `attempts`, `lastError?`, `sentAt?`,
`createdAt`/`updatedAt`. RLS scoped by `tenantId`. **Every channel's
dispatch path updates this row to its true outcome** — including in-app:
if the Redis publish itself throws, the row is written `FAILED`, not left
`SENT` (the source's bug: `dispatchInApp` wrote `SENT` before attempting
delivery and only logged a warning on publish failure, so the log lied
about outcome).

**`DigestQueueEntry`** — `tenantId`, `userId`, `email`, `channel`,
`scheduledFor`, `status` (enum `PENDING`/`PROCESSING`/`SENT`/`FAILED`),
`notificationIds` (`String[]`, native Postgres array — replaces the
source's Mongo array field), `attempts`, `lastError?`, `sentAt?`,
`createdAt`/`updatedAt`. RLS scoped by `tenantId`.

**`WebhookEndpoint`** — `tenantId`, `userId`, `url`, `secret`, `enabled`,
`description?`, `createdAt`/`updatedAt`. RLS scoped by `tenantId`.

**`EmailSuppression`** — `email` (`@unique`), `reason`, `suppressedAt`.
Global, not tenant-scoped (a bounce/complaint suppression list is a
property of the email address itself, not of any one tenant) — no RLS
policy on this table, by design.

**Explicitly not modeled**: no `SagaState`, no `OutboxEvent`/
`OutboxDeadLetter` — the saga/outbox durability layer is out of scope
(see Scope).

### Template registry

```ts
export interface NotificationContent {
  title: string;
  body: string;
  html?: string; // only the email channel renders this
}
export type TemplateBuilder = (data: Record<string, unknown>) => NotificationContent;

export function registerTemplate(eventType: string, builder: TemplateBuilder): void;
```

Content is channel-neutral by construction — the source modeled every
channel's content as an `EmailContent` shape (`badge/h2/bodyText/details/
ctaText/ctaUrl/closingText`) and derived `title`/`body` for non-email
channels by joining `details` label:value pairs with `" | "`, a lossy
transformation forced onto channels that never wanted HTML-shaped input.
Gen_NOTIF's `NotificationContent` has exactly the fields every channel
actually needs; `html` is optional and ignored by non-email channels.

No CPMS-specific templates (the ~40 `com.*`/`reg.*`/`pmt.*` builders)
ship with the library — those are host content, registered by the host
application at `createGenNotif()` time or any time before `notify()` is
called for that `eventType`. An eventType with no registered builder
falls back to a generic default (`{title: "New notification", body:
eventType}`).

### The `notify()` pipeline

```
notify({ tenantId, recipients: [{userId, email?, phone?}], eventType, data, entityRef? })
```

For each recipient, in parallel (`Promise.allSettled`, one recipient's
failure never blocks another's):

1. `preferenceRepo.findByEventType(tenantId, userId, eventType)` →
   `resolveChannels(prefs)`: filter `enabled === true`; if no preference
   rows exist for this `(tenantId, userId, eventType)` at all, default to
   `[{channel: "inapp", digestMode: false}]` (matches the source's
   fail-open-to-in-app default).
2. For each resolved `{channel, digestMode}`:
   - Render `NotificationContent` via the template registry.
   - `digestMode === true` and `channel === "email"` → write a
     `QUEUED_FOR_DIGEST` log row, then `enqueueForDigest()` (see below)
     instead of sending immediately. `digestMode` has no effect on
     non-email channels in v1 (in-app/webhook/SMS are always immediate —
     "digest" is inherently an email-batching concept).
   - Otherwise, dispatch via the matching channel port and write the log
     row reflecting the **real** outcome (`SENT` or `FAILED`, with
     `lastError` populated on failure).
3. A channel dispatch error is caught, logged, and does not abort the
   recipient's other channels or any other recipient — matches the
   source's resilience behavior, worth keeping as-is.

No saga/outbox wraps this. `notify()`'s durability is exactly "the
in-memory guarantee of one async function call" — documented plainly in
`integration-guide.md` as a known v1 limitation, the same way Gen_TBR
documents that DNS verification is on-demand-only rather than building a
re-verification worker to paper over it.

### Digest batching

`enqueueForDigest(tenantId, userId, email, notificationId)`: upserts a
`PENDING` `DigestQueueEntry` keyed on `(tenantId, userId, email, channel:
EMAIL, status: PENDING)`, appending `notificationId` to `notificationIds`
and setting `scheduledFor` only on insert (default: a configurable
`nextDigestBoundary()` function, defaulting to next-top-of-hour but
overridable via config — the source hardcoded hourly with no override).

`runDigestSweep()`: host-triggered (no internal cron). Atomically claims
up to a batch limit of due (`scheduledFor <= now`), `PENDING` entries
(`status` flip to `PROCESSING` in the claiming query, avoiding
double-processing across concurrent callers), and for each: loads every
referenced `NotificationLog` row by `notificationIds`, renders a **real**
digest email listing each notification's actual `title` (and `body` if
present) — fixing the source's bug where the digest email only ever said
"You have N notification(s) while you were away" with zero per-item
content — sends via `IEmailSender`, marks the entry `SENT`, and updates
every referenced `NotificationLog` to `SENT`.

### Realtime gateway (`SocketIoRedisGateway`)

`attach(io)`:
- `io.use(...)` — JWT handshake middleware: reads the token from
  `socket.handshake.auth.token` first, falls back to an `Authorization:
  Bearer` header; verifies via `IJwtVerifier`; on success sets
  `socket.data.{userId, tenantId, roles}`; on failure calls
  `next(new Error("GEN_NOTIF_UNAUTHORIZED"))`.
- On `connection`: `socket.join(userRoom(tenantId, userId))` where
  `userRoom(tenantId, userId) = "tenant:${tenantId}:user:${userId}"` —
  the same room-naming convention the source's `com-svc` already used,
  kept because tenant-scoped rooms are a sound pattern, not because of
  any coupling to `com-svc` itself.
- Mounts the pub/sub bridge: a dedicated `ioredis` connection (a
  `.duplicate()` of the gateway's own Redis client — a subscriber-mode
  Redis connection can't issue normal commands, so it must be separate
  from whatever client issues `PUBLISH`), `psubscribe("tenant:*:notif:*")`,
  and on every `pmessage` parses `tenantId`/`userId` back out of the
  channel name and re-emits into `userRoom(tenantId, userId)` as a
  `"notification"` event with the raw payload.

`publishInApp(tenantId, userId, payload)`: `redis.publish(
  buildNotifChannel(tenantId, userId), JSON.stringify(payload))` where
`buildNotifChannel` and the room-naming function share **one** exported
constant/helper pair — the source had this exact contract (channel name
format `tenant:{tenantId}:notif:{userId}`) implicitly duplicated as
separate template-literal constructions in two different services with
no shared source of truth; here it's one function, used by both the
publish side and the subscribe side, because both live in the same
package.

The Socket.IO server itself (`new SocketIoServer(httpServer, {path:
"/gen-notif/ws", adapter: createAdapter(pub, sub), cors: {origin:
config.allowedOrigins}, transports: ["websocket", "polling"]})`,
`pub`/`sub` being two more `.duplicate()`s of the Redis client) is
created inside `attachRealtime()`, not `attach()` — `attach()` operates
on an already-constructed `SocketIoServer` so the same `IRealtimeGateway`
port could, in principle, be given a server a host constructed some
other way.

### Webhook dispatch detail

```ts
function sign(secret: string, timestamp: number, body: string): string {
  const data = `${timestamp}.${body}`;
  return `sha256=${createHmac("sha256", secret).update(data).digest("hex")}`;
}
export function verifyWebhookSignature(secret: string, timestamp: number, rawBody: string, signature: string): boolean {
  if (Math.abs(Date.now() / 1000 - timestamp) > 300) return false;
  return timingSafeEqual(Buffer.from(sign(secret, timestamp, rawBody)), Buffer.from(signature));
}
```

Fan-out is parallel (`Promise.allSettled`) across every `enabled`
endpoint for the `(tenantId, userId)` pair; each endpoint gets its own
`NotificationLog` row recording the **final** outcome of its retry
sequence (not one row per attempt). Retry: `[0, 1000, 4000]`ms delays, 3
attempts total, `AbortSignal.timeout(10_000)` per attempt, each attempt
re-signs with a fresh timestamp. This retry is in-process and
synchronous — a process crash mid-retry loses the remaining attempts.
Documented as a known v1 limitation (a queue-backed retry is a Phase 2
option, not built now). No SSRF guard on endpoint URLs in v1 — noted as a
known limitation in `integration-guide.md` (the source had this gap too;
a host that lets tenants register arbitrary webhook URLs should apply its
own egress policy).

### Error taxonomy

`AppError` base class (`statusCode`, `code`, `message`), copied verbatim
from Gen_REG/Gen_TBR's shape. Subclasses: `NotificationNotFoundError`
(404), `NotificationForbiddenError` (403 — a `markRead` call for a
notification owned by a different `tenantId`/`userId`),
`PreferenceNotFoundError` (404), `WebhookEndpointNotFoundError` (404),
`SmsNotConfiguredError` (501 — thrown by the default `ISmsSender`, making
"not implemented" a real thrown error instead of a silently-logged fake
success), `ChannelDispatchError` (502 — wraps an unexpected channel
adapter failure that isn't one of the above). `GenNotifConfigError` is a
plain `Error` subclass (no HTTP status, boot-time only), matching
`GenTbrConfigError`/`GenRegConfigError`.

Every error response shape: `{ error: code, message: humanText }`.

### API surface (v1)

Base path `/api/v1`, internal path `/internal`. Every mutating endpoint
requires either an `X-Internal-Secret` header or in-process invocation —
the factory enforces no auth itself, matching every sibling.

- `POST /internal/notify` — HTTP-callable form of the `notify()`
  function, for hosts that prefer an HTTP boundary over an in-process
  import.
- `GET /preferences?tenantId=&userId=` — list a user's preferences.
- `PATCH /preferences` — upsert one `(eventType, channel)` preference.
- `GET /preferences/event-types` — returns the `eventType` keys
  **currently registered in the template registry** (derived live, not a
  separately hardcoded catalog) — this removes the source's two
  independent sources of truth (`event-catalog.ts` vs. the template
  registry could silently drift apart); here there is exactly one.
- `GET /notifications/unread?tenantId=&userId=&limit=&before=` —
  paginated unread list (`limit` capped at 100, Zod-validated).
- `PATCH /notifications/:id/read` — atomic ownership-checked mark-read
  (the update's `WHERE` clause includes `tenantId`/`userId`, not a
  separate read-then-write — closes the TOCTOU gap by construction, the
  one thing the source already did correctly).
- `GET/POST/PATCH/DELETE /webhook-endpoints` (if
  `modules.webhookEndpoints`) — CRUD for a tenant/user's registered
  webhook URLs.
- `POST /internal/digest-sweep` — HTTP-callable form of
  `runDigestSweep()`, for hosts that prefer to trigger it via an HTTP
  cron caller rather than an in-process import.
- `GET /health` → `{ status: "ok" }`, unauthenticated.

## Data Flow & Error Handling

**Trigger → preference resolution → dispatch → log**: see the `notify()`
pipeline above. The whole path is synchronous from the caller's
perspective — `notify()` resolves only after every recipient's every
resolved channel has been attempted (successes and failures alike);
callers wanting fire-and-forget semantics call it without awaiting, which
is their choice to make, not something Gen_NOTIF hides behind a queue.

**Digest accumulation → sweep → send**: a `digestMode` preference
redirects email dispatch into `enqueueForDigest()` instead of sending
immediately; `runDigestSweep()` is the only place those accumulated
notifications ever actually reach an inbox. If a host never calls
`runDigestSweep()`, digest-mode notifications accumulate forever and are
never sent — documented explicitly as a required integration step, not
an implicit background guarantee.

**In-app delivery failure**: if `IRealtimeGateway.publishInApp()` throws
(Redis unreachable, etc.), the `NotificationLog` row is written `FAILED`
with the real error in `lastError` — the caller (and the unread-list
query) can tell the difference between "delivered in real time" and
"logged but not pushed live," unlike the source.

**Webhook delivery failure**: after all 3 retry attempts are exhausted
for a given endpoint, that endpoint's `NotificationLog` row is `FAILED`
with the last HTTP/network error recorded; other endpoints for the same
recipient are unaffected (`Promise.allSettled` fan-out).

## Testing

Mirrors Gen_TBR's tiering:

- **Unit tests** (Vitest, no DB) — channel resolution (`resolveChannels`
  including the no-preference-rows fallback), template registry fallback
  behavior, HMAC sign/verify (including the timestamp-grace-window
  rejection), digest bucket-key/upsert logic against a mocked repo, the
  `SmsNotConfiguredError` throw path.
- **Repository integration tests** (real Postgres via
  `@testcontainers/postgresql`, migrations applied via `prisma migrate
  deploy`) — RLS actually enforces isolation (including the `FORCE` +
  `WITH CHECK` behavior specifically, since that's the exact gap the
  source shipped with a bug in), preference upsert-on-conflict, the
  atomic ownership-checked `markRead` update, digest-entry claim query
  under concurrent callers.
- **Realtime tests** — a real `socket.io-client` against an ephemeral
  `http.Server` with `attachRealtime()` mounted, a fake `IJwtVerifier`,
  and a real Redis via Testcontainers for at least one true end-to-end
  publish→bridge→client-receives test; additional unit-level tests use an
  injectable in-memory pub/sub fake for speed.
- **HTTP tests** (`supertest` against the built `app`) — every endpoint's
  happy path, the internal-secret gate on mutating routes, the
  `event-types` endpoint reflecting live-registered templates.
- **Factory/resolver tests** — `createGenNotif({})` boots cleanly with
  all env set; throws `GenNotifConfigError` listing every missing var
  with none set; a disabled module's routes genuinely 404.
- **Smoke script** (`scripts/smoke-notif.sh`) — register a template,
  register a webhook endpoint, set a preference, call `notify()`,
  connect a `socket.io-client` and assert the `"notification"` event
  arrives, verify the webhook receiver got a correctly-signed payload,
  fetch the unread list, mark read, run a digest sweep end-to-end against
  the demo app.

## Delivery Approach

Same subagent-driven-development cadence as every other Gen_MS library:
this spec → an implementation plan broken into small, independently
reviewable tasks → one fresh implementer subagent per task → task-level
spec-compliance + quality review → fix loop as needed → a final
whole-branch review once all tasks land → finish the branch.

Unlike Gen_TBR (blank boundary, no source), and like Gen_ADM, this build
has real `pre-context/` source to draw from — `pre-context/notif-svc` and
a notification-relevant slice of `pre-context/com-svc` (its `ws/`,
`realtime/`, and JWT-auth-handshake files only; the chat/messaging
modules are deliberately not copied in, since they're out of scope and
would only invite accidental scope creep during implementation).

Build order, bottom-up: repo scaffolding (workspace `package.json`, both
sub-packages, `tsconfig.json`/`vitest.config.ts` copied from Gen_REG,
`docker-compose.yml` on the next free port after Gen_TBR's 5437 — **5438**
— `.gitignore`, `.env.example`) → Prisma schema + migrations (RLS with
`FORCE`+`WITH CHECK` from migration 1) → ports → default adapters
(`PrismaTenantPreferenceRepo`, `PrismaNotificationLogRepo`,
`PrismaDigestQueueRepo`, `PrismaWebhookEndpointRepo`,
`NodemailerEmailSender`, `UnimplementedSmsSender`, `SocketIoRedisGateway`,
`JwksJwtVerifier`) → template registry → `NotifService` (the `notify()`
pipeline) → `PreferenceService` → `NotificationService` (unread/read) →
`WebhookEndpointService` + webhook channel dispatch → `DigestService` →
controllers/routers → the `createGenNotif` factory (incl.
`attachRealtime`) → the demo app (with a small `socket.io-client`
connection in its smoke script) → `integration-guide.md` + `README.md` +
the smoke script.

## Assumptions (to be confirmed with the user before writing the
implementation plan)

1. Gen_NOTIF ships the **full** realtime stack (Socket.IO server + Redis
   adapter + pub/sub bridge) as one self-contained piece via
   `attachRealtime(httpServer)` — not split across two services the way
   the CPMS source happened to split it.
2. The trigger API is a direct function call / internal HTTP endpoint —
   no RabbitMQ, no CPMS event envelope, no outbox/saga durability
   machinery. `notify()`'s durability is exactly the caller's own retry
   policy.
3. Templates are host-registered at runtime (`registerTemplate`) — zero
   hardcoded CPMS-specific templates ship with the library.
4. Email is single-tier `nodemailer` SMTP — no AWS-SES-specific special
   casing, no promised-but-unimplemented third tier.
5. SMS ships as a port only; the default adapter throws
   `SmsNotConfiguredError` rather than silently logging fake success.
6. No internal scheduler — `runDigestSweep()` (and any future periodic
   job) is host-triggered, matching Gen_TBR's "no background worker in
   v1" precedent.
7. All persistence is Postgres/Prisma; the Mongo→Postgres migration is
   this design, not a follow-up. RLS ships `FORCE` + `WITH CHECK` from
   the first migration.
8. FMM-style plan/entitlement gating is replaced by an optional
   host-supplied `channelGate` predicate, default always-allow.
9. SSE / RBAC-permission-sync is entirely out of scope — confirmed a
   separate concern from notification delivery in the source.
10. Digest emails list each notification's real title/body (fixing the
    source's contentless-count-only bug); the digest interval is a
    configurable function, defaulting to next-top-of-hour.
