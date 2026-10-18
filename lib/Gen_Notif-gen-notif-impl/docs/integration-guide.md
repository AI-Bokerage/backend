# Gen_NOTIF Integration Guide

## Embedding in-process

```ts
import { createGenNotif, registerTemplate } from "@gen-ms/gen-notif-starter";

registerTemplate("doc.uploaded", (data) => ({
  title: "New document uploaded",
  body: `${data["fileName"]} was uploaded by ${data["uploadedBy"]}`,
}));

const genNotif = createGenNotif({
  internalSecret: process.env.GEN_NOTIF_INTERNAL_SECRET,
});

hostApp.use("/notif", genNotif.app);
genNotif.attachRealtime?.(hostHttpServer);
```

## Running standalone (HTTP)

```bash
docker compose up -d
npx prisma migrate deploy --schema packages/gen-notif-starter/prisma/schema.prisma
npm run build --workspaces --if-present
node packages/gen-notif-demo/dist/index.js
```

## Environment variables

| Var | Required when | Notes |
|---|---|---|
| `DATABASE_URL` | any Prisma-backed repo (`preferenceRepo`/`notificationRepo`/`digestRepo`/`webhookRepo`) not overridden | Postgres connection string |
| `REDIS_URL` | `modules.realtime` enabled and `realtimeGateway` not overridden | Backs the Socket.IO Redis adapter and the in-app publish/subscribe bridge |
| `JWKS_URL` | `modules.realtime` enabled and neither `jwtVerifier` nor `realtimeGateway` overridden | JWKS endpoint for verifying Socket.IO handshake tokens |
| `JWT_ISSUER` | same as `JWKS_URL` | Expected `iss` claim |
| `SMTP_HOST` | `emailSender` not overridden | SMTP server host |
| `SMTP_PORT` | never (defaults to `587`) | |
| `SMTP_USER` / `SMTP_PASS` | only if the SMTP server requires auth | |
| `SMTP_SECURE` | never (defaults to `false`) | `"true"` for implicit TLS |
| `SMTP_FROM` | never (defaults to `no-reply@example.com`) | |
| `GEN_NOTIF_INTERNAL_SECRET` | `internalSecret` config not set | Gates `/internal/notify` and `/internal/digest/*` via `X-Internal-Secret` |
| `ALLOWED_ORIGINS` | never (defaults to `*`) | Comma-separated CORS origins, also used for the Socket.IO CORS config |

## API surface

Internal (require `X-Internal-Secret: <GEN_NOTIF_INTERNAL_SECRET>`):

| Method | Path | Body/Query | Notes |
|---|---|---|---|
| POST | `/internal/notify` | `{ tenantId, recipients: [{userId, email?, phone?}], eventType, data, entityRefType?, entityRefId? }` | Fans out to every enabled channel per recipient's preferences |
| POST | `/internal/digest/sweep` | — | Runs `runDigestSweep()` once; host is responsible for scheduling |

Public (`modules.*` gated, no auth of its own — front with the host's own authn/authz):

| Method | Path | Body/Query | Notes |
|---|---|---|---|
| GET | `/api/v1/preferences` | query `tenantId`, `userId` | List preferences |
| PATCH | `/api/v1/preferences` | `{ tenantId, userId, eventType, channel, enabled, digestMode }` | Upsert one preference row |
| GET | `/api/v1/preferences/event-types` | — | Lists every `registerTemplate`-ed event type |
| GET | `/api/v1/notifications/unread` | query `tenantId`, `userId`, `limit?`, `before?` | Unread + queued-for-digest notifications, newest first |
| PATCH | `/api/v1/notifications/:id/read` | `{ tenantId, userId }` | Marks one notification read; 404s if it doesn't belong to that tenant/user |
| GET | `/api/v1/webhook-endpoints` | query `tenantId`, `userId` | List endpoints |
| POST | `/api/v1/webhook-endpoints` | `{ tenantId, userId, url, description? }` | Creates an endpoint with a server-generated secret |
| PATCH | `/api/v1/webhook-endpoints/:id` | `{ tenantId, url?, enabled?, description? }` | |
| DELETE | `/api/v1/webhook-endpoints/:id` | `{ tenantId }` | |

Always available:

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | No auth |
| GET | `/docs` | Swagger UI, browses the OpenAPI spec below |
| GET | `/docs.json` | Raw OpenAPI 3.0 spec for every route in this table |

**Gen_NOTIF ships no working SMS provider — configure `smsSender` with your own Twilio/SNS/etc. adapter, or SMS dispatch will always fail with `SMS_NOT_CONFIGURED`.**

**Gen_NOTIF has no internal scheduler. You must call `runDigestSweep()` (or `POST /internal/digest/sweep`) on your own cron/interval, or digest-mode notifications will accumulate forever and never send.**

**`notify()`'s durability is exactly the guarantee of one in-process async call — there is no outbox, no retry queue, no message broker. If your delivery requirements need at-least-once guarantees across process restarts, wrap `notify()` in your own durable job.**

## Worked example

```ts
import { createGenNotif, registerTemplate } from "@gen-ms/gen-notif-starter";
import { io } from "socket.io-client";

registerTemplate("doc.uploaded", (data) => ({
  title: "New document uploaded",
  body: `${data["fileName"]} was uploaded by ${data["uploadedBy"]}`,
}));

const genNotif = createGenNotif({});
const server = require("node:http").createServer(genNotif.app);
genNotif.attachRealtime?.(server);
server.listen(3500);

// host sets a preference for this recipient first:
// PATCH /api/v1/preferences { tenantId, userId, eventType: "doc.uploaded", channel: "inapp", enabled: true, digestMode: false }

const socket = io("http://localhost:3500", {
  path: "/gen-notif/ws",
  auth: { token: "<jwt for this user, verified against JWKS_URL>" },
});
socket.on("notification", (payload) => console.log("received:", payload));

await genNotif.notify({
  tenantId: "...",
  recipients: [{ userId: "...", email: "user@example.com" }],
  eventType: "doc.uploaded",
  data: { fileName: "invoice.pdf", uploadedBy: "Alice" },
});
// -> socket receives an InAppPayload over the "notification" event
```

## Swapping adapters

- `preferenceRepo` / `notificationRepo` / `digestRepo` / `webhookRepo`: implement `ITenantPreferenceRepo` / `INotificationLogRepo` / `IDigestQueueRepo` / `IWebhookEndpointRepo` to swap persistence.
- `emailSender`: implement `IEmailSender` (`send`) for SES, Postmark, etc. instead of SMTP via Nodemailer.
- `smsSender`: implement `ISmsSender` (`send`) — required for any real SMS delivery, the default always throws `SMS_NOT_CONFIGURED`.
- `realtimeGateway`: implement `IRealtimeGateway` (`attach`/`publishInApp`) to swap Socket.IO+Redis for another realtime transport.
- `jwtVerifier`: implement `IJwtVerifier` (`verify`) to use a different token format or issuer than JWKS.
- `channelGate`: `(tenantId, channel) => boolean | Promise<boolean>` — a synchronous/async kill-switch checked before every per-recipient channel dispatch, e.g. to enforce a tenant's plan-tier channel entitlements.

## Known limitations (v1)

- No working SMS provider out of the box.
- No internal scheduler — digest sweeps and any retry policy are entirely host-driven.
- `notify()` has no outbox/retry queue/message broker; delivery is best-effort within one call.
