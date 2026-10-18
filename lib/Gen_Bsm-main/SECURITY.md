# Security Policy

## Reporting a vulnerability

If you discover a security vulnerability in `bsm-core`, `bsm-spring-boot-starter`,
or `bsm-svc`, do not open a public issue. Report it privately to the
maintaining team through your organization's internal security-disclosure
channel. Include:

- Which module(s) are affected (`bsm-core`, `bsm-spring-boot-starter`, `bsm-svc`)
- The version(s) affected
- Steps to reproduce, or a minimal proof of concept
- The impact you believe the vulnerability has

You should expect an initial acknowledgement within a reasonable window and
a follow-up once the issue has been triaged. Fixes for confirmed
vulnerabilities are released as patch versions per `VERSIONING.md`, with the
`CHANGELOG.md` entry calling out the security fix explicitly (without
disclosing exploit details before a fix is available downstream).

## Scope

This policy covers the library and starter (`bsm-core`,
`bsm-spring-boot-starter`) as published artifacts, and the reference host
application (`bsm-svc`) in this repository. It does not cover:

- Deployment-specific configuration mistakes (e.g. leaving
  `bsm.internal-secret` at its shipped default `change-me-in-production` —
  see `CONFIGURATION_REFERENCE.md`; this is a configuration responsibility
  of anyone deploying `bsm-svc`, not a library vulnerability)
  - Vulnerabilities in third-party dependencies (Stripe SDK, Razorpay SDK,
  Spring Boot itself, etc.) — report those to the upstream project; a
  dependency bump to pick up their fix is tracked here as a normal patch
  release once available

## Security-relevant design notes for integrators

- `bsm-core` never handles authentication itself — it delegates tenant
  isolation to `TenantScopePort` and (where applicable)
  `BsmAuthorizationPort`, both of which the host must implement against its
  own real authentication mechanism. See `PORT_REFERENCE.md`.
- Payment credentials (Stripe/Razorpay API keys) are never held by
  `bsm-core` — they belong to the host's `PaymentGatewayPort`
  implementation. `bsm-core` only ever sees a resolved `PaymentGatewayPort`
  instance via `PaymentGatewayResolver`.
- `bsm-svc`'s shipped `application.yaml` includes an insecure default for
  `bsm.internal-secret` (`change-me-in-production`) — this MUST be
  overridden via environment variable in any real deployment. This is
  tracked as a known operational risk, not a code defect; see
  `CONFIGURATION_REFERENCE.md` and `TECHNICAL_DEBT_REGISTER.md`.

## Supported versions

See `COMPATIBILITY.md` for which major/minor versions currently receive
security patches.
