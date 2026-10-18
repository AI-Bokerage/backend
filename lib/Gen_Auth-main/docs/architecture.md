# Auth Service

> JWT authentication service — issues, validates, and revokes tokens for all CPMS tenants.

## Overview

auth-svc is the identity gateway for the platform. Every inbound request from a browser or API client passes through auth-svc to obtain a signed JWT. Downstream services validate that JWT locally using JWKS, so auth-svc is not on the hot path for every request — only for login, logout, and token refresh.

## Architecture

```mermaid
graph LR
    Client([Client / API Gateway])
    AUTH[auth-svc :8101]
    TDB[(Tenant DB\nPostgreSQL)]
    ADM[adm-svc :8102]
    MQ([RabbitMQ\ncpms.events])
    JWKS([JWKS endpoint\npublic])

    Client -->|POST /auth/login| AUTH
    Client -->|POST /auth/logout| AUTH
    Client -->|GET  /auth/jwks| JWKS
    AUTH -->|validate user| ADM
    AUTH -->|read tenant config| TDB
    AUTH -->|publish auth events| MQ
    AUTH -->|issues| JWKS
```

## Key Domains

- **Authentication**: username/password login, token issuance, logout/invalidation
- **Token lifecycle**: JWT generation (RS256), refresh token rotation, revocation list
- **JWKS**: public key endpoint consumed by all downstream services for local validation
- **Tenant resolution**: looks up tenant DB config per request to scope issued tokens

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Runtime | Java 21 |
| Framework | Spring Boot |
| Database | PostgreSQL (Spring Data JPA) |
| Messaging | RabbitMQ |
| Token format | JWT RS256 |

## Message Topology

**Publishes:**
- `auth.login.success` on `cpms.events`
- `auth.logout` on `cpms.events`

**Consumes:**
- None (push-only service)
