# Security

This repository is internal to the CPMS platform ecosystem (see `LICENSE`) — there is no public vulnerability disclosure program.

## Reporting a concern

Report suspected security issues (in `ppm-core`, `ppm-spring-boot-starter`, `ppm-svc`, or `ppm-demo`) directly to the CPMS Platform Team through your normal internal channel, not as a public GitHub issue.

## Scope notes specific to this repository

- `ppm-core` and `ppm-spring-boot-starter` contain no security logic themselves — actor identity, authentication, and authorization are host concerns by design (see `ppm-core/ARCHITECTURE.md`). A security report against these two modules is almost always either (a) a business-rule bug with security implications (e.g. a missing uniqueness check) or (b) a supply-chain concern about a declared dependency.
- `ppm-svc` owns JWT validation, the authorization filter chain, and Redis-backed role/permission resolution (`infrastructure/security/`) — most application-level security concerns belong here.
- `ppm-demo` is a validation artifact with no security stack at all (see its README) — not a target for security review, and not representative of production security posture.
