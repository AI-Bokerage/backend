# auth-svc — API Flow Documentation Index

> **This file is an index only.**
> For full diagrams and step-by-step breakdowns, see the individual flow files below.

## APIs

| File | Endpoint | Purpose |
|---|---|---|
| [login-flow.md](login-flow.md) | `POST /api/v1/auth/login` | Authenticate with email + password, issue JWT + refresh token |
| [refresh-flow.md](refresh-flow.md) | `POST /api/v1/auth/refresh` | Rotate the refresh token, issue new JWT + refresh token |
| [session-flow.md](session-flow.md) | `GET /api/v1/auth/session` | Validate JWT and return current user + permissions |
| [logout-flow.md](logout-flow.md) | `POST /api/v1/auth/logout` | Revoke session, clear cookies, publish lifecycle event |
| [change-password-flow.md](change-password-flow.md) | `POST /api/v1/auth/change-password` | Change user password, revoke all other active sessions, publish change event |
| [magic-link-flow.md](magic-link-flow.md) | `POST /api/v1/auth/magic-link/issue` & `verify` | Generate and verify magic link for password reset, clearing all cookies |
| [super-admin-flow.md](super-admin-flow.md) | `POST /api/v1/auth/super-admin/...` | Super Admin login, reset password issue, and reset password verify |
| [jwks-flow.md](jwks-flow.md) | `GET /.well-known/jwks.json` | Public JWKS endpoint exposing active and overlapping RSA public keys |
| [dual-boot-features.md](dual-boot-features.md) | Infrastructure Configuration | Dual-boot features documenting KMS vs Local signing modes and SES vs SMTP mail backends |

---

## Messaging topology

| File | Area | Purpose |
|---|---|---|
| [event-topology.md](event-topology.md) | RabbitMQ / domain events | Documents AUTH-SVC as a producer-first publisher on `cpms.events` with reserved consumer topology for future platform event consumption |

---

## Key architectural decisions captured in this version

### Absolute session expiry (anti-immortal-session)
Login stamps `absoluteExpiresAt = now + 7d` **once** on the first refresh token.
Every rotation carries it forward unchanged. The remaining window (`absoluteExpiresAt - now`)
drives the cookie `Max-Age` and Redis TTL at each rotation, so sessions cannot extend beyond
the original login-time boundary no matter how frequently the user is active.

### Replay detection vs natural expiry (false-positive fix)
When a refresh token hash is absent from Redis, the system consults the Postgres audit log:
- `auditRecord.expiresAt > now` → token was already rotated but not yet expired → **genuine replay attack** → family revoked + session deactivated
- `auditRecord.expiresAt <= now` → Redis TTL simply elapsed → **normal lifecycle event** → session deactivated quietly + `auth.logout` event published

Previously, both cases triggered `WARN security.replay_attack`, flooding security alerts with false positives.

### `auth.logout` event published in four paths
`AuthEventPublisher.publishLogout()` is called from:
1. Explicit `POST /logout`
2. Natural token expiry (Redis TTL elapsed)
3. Absolute session boundary reached
4. Replay attack forced revocation

Downstream consumers (audit-svc, notif-svc) only need to subscribe to `auth.logout`
to react to any session end, regardless of the termination reason.

### Async critical-path optimisation (login)
Login offloads all non-blocking side-effects to the `authAsync` virtual-thread pool:
login-attempt record, audit log, RabbitMQ event, PG refresh-token archive.
The response is returned as soon as Redis write completes — typically < 200 ms end-to-end.

---


## Architecture summary

```
┌────────────┐  POST /login          ┌──────────────────┐
│            │ ─────────────────────▶│  LoginServiceImpl │
│            │                       │  (critical path:  │
│   Client   │                       │  Argon2 + Redis)  │
│  (browser) │  POST /refresh        └──────────────────┘
│            │ ─────────────────────▶┌──────────────────────┐
│            │  GET  /session        │ RefreshTokenServiceImpl│
│            │ ─────────────────────▶│ (Redis authoritative  │
│            │  POST /logout         │  token store)         │
│            │ ─────────────────────▶└──────────────────────┘
└────────────┘
                                     Token stores:
                                     ┌────────────────────────────────────────────┐
                                     │ Redis          — active-token registry,    │
                                     │                  permission cache,          │
                                     │                  lockout counters           │
                                     │ PostgreSQL      — sessions, users,         │
                                     │                  refresh-token audit log   │
                                     └────────────────────────────────────────────┘
                                     Async:
                                     ┌────────────────────────────────────────────┐
                                     │ authAsync pool — login/audit side-effects  │
                                     │ RabbitMQ       — publishes to cpms.events  │
                                     │                  using auth.* routing keys │
                                     └────────────────────────────────────────────┘
```
