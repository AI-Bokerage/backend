# ADR-002 — `ppm-core` Extraction Pattern (Library Boundaries, Package Convention, Migration Checklist)

**Status:** Accepted
**Date:** 2026-07-22
**Affects:** All PPM aggregates not yet migrated into `ppm-core` (Module, AddOn, Entitlement, PromoCode, PlanAddOn, PlanPrice, PlanEntitlement, PlanModule, AddOnPrice — and PlanVersion's own use-case layer, currently only its read-only model/port live in `ppm-core`)

---

## Context

`ppm-svc` is being extracted from the CPMS-Platform monorepo into a standalone repository (`Gen_PPM`). Beyond standalone buildability (already done — zero dependency on `libs:java-common`), the goal is for the reusable business logic to become a genuine library (`ppm-core`) that any Spring Boot application can depend on, implement the outbound ports for, and get PPM's business rules for free. `ppm-svc` becomes the first *host* — a reference implementation providing REST, persistence, security, and messaging around the library.

The Plan aggregate was migrated first, as a proof of the extraction pattern. It compiles independently, `ppm-svc` compiles against it, and the full 353-file test suite is green. This ADR captures the pattern established during that migration so every subsequent aggregate follows it consistently, instead of re-deriving conventions per aggregate.

---

## Decision

### Library boundaries — what `ppm-core` owns

- Domain models (aggregates, entities, value objects, enums) — framework-independent, no JPA/Spring MVC/security annotations.
- Domain events.
- Repository ports (outbound) — interfaces only; no implementation.
- Use-case interfaces and their implementations — operate exclusively on domain models and primitives (UUID, String, enums, etc.), never on host-specific request/response DTOs.
- Domain exceptions.

### Host boundaries — what `ppm-svc` (and any future host) owns

- Controllers, request/response DTOs, OpenAPI annotations.
- DTO ↔ domain mapping (MapStruct mappers) — this now happens at the controller boundary, not inside the use-case implementation.
- Security (JWT conversion, authorization filters, actor-identity extraction from `SecurityContextHolder`).
- Persistence adapters (JPA entities, Spring Data repositories, adapter classes implementing the ports).
- Messaging (RabbitMQ config, exchange/queue topology, publishers/consumers).
- Scheduling, Spring Boot auto-configuration, `application.yaml`.

**Actor identity is a host concern.** The Plan migration found the use-case implementation calling `SecurityUtils.requireCurrentUserId()` (backed by Spring Security's `SecurityContextHolder`) directly — a real coupling to a specific security stack, not just a DTO-shape issue. The fix: use-case methods that need an acting user take `UUID actorId` as an explicit parameter; the host (controller) resolves it and passes it in. Apply this to every remaining aggregate's create/update/delete operations.

### Package convention — feature-first, then DDD concept

`ppm-core` is organized by business capability first, DDD concept second:

```
com.company.ppmsvc
├── common/              # BaseEntity, AuditableEntity, DomainEvent — foundational, cross-aggregate
├── exception/           # BusinessException, ErrorCode, ResourceNotFoundException, etc. — shared, not per-aggregate
└── <aggregate>/         # e.g. plan, pricing, entitlement, module, promo
    ├── model/           # domain model classes + aggregate-local enums
    ├── port/            # outbound repository ports (only create inbound/ subpackage if more than one inbound port style is ever needed — YAGNI otherwise)
    └── usecase/         # use-case interface + implementation (impl stays in the same package, not a nested impl/ subpackage)
```

Only create a subpackage when it has something to put in it. The Plan slice has no `policy/` or `workflow/` package because Plan has no cross-aggregate business rules yet — do not pre-create empty packages for a target shape that doesn't apply yet.

Avoid inside `ppm-core`: `api`, `controller`, `infrastructure`, `persistence`, `adapter`, service-oriented `config`. These are host-layer names and their presence in the library is itself a signal that something host-specific leaked in.

### Port design

- One outbound port interface per aggregate root repository (`PlanRepositoryPort`, `PlanVersionRepositoryPort`, ...). Ports operate only on domain models — no JPA entity, no DTO, ever crosses this boundary.
- The use-case interface itself is the inbound port in hexagonal terms — no separate `port.inbound` package is needed unless a second inbound entry point (e.g. a message-driven trigger) appears for the same use case.

### Dependency rules for `ppm-core`

Allowed: Java standard library, `spring-context`, `spring-tx` (for `@Service`/`@Transactional` — genuinely useful wiring, not a leak), `slf4j-api`, Lombok, `jackson-annotations` (see note below).

Forbidden: Spring MVC/Web, Spring Security, RabbitMQ/Kafka client libraries, JPA/`EntityManager`, any persistence adapter, scheduling, anything HTTP-shaped.

**Jackson annotation note:** `PlanVisibility` carries `@JsonValue`/`@JsonCreator` for its wire-value serialization. This is a real, if light, coupling to a specific JSON library — flagged rather than silently justified. It is currently accepted as part of the model's public contract (the wire value *is* domain vocabulary — "public"/"private"/"legacy" — not a transport detail), but if a future host needs a non-Jackson serialization stack, this becomes a genuine porting cost. Revisit if that need materializes; don't add more Jackson annotations to new aggregates without the same conscious call.

---

## Migration Checklist (per aggregate)

1. **Analyze** — list the aggregate's domain model(s), use-case interface, repository port(s), and every `api.dto`/`api.mapper` import in its application-layer impl. Identify any direct host-framework dependency (Spring Security, HTTP status codes, etc.) inside the use-case impl.
2. **Extract** — move domain model, port, and use-case files into `ppm-core` under `com.company.ppmsvc.<aggregate>.{model,port,usecase}`. Keep class names stable (no renaming for its own sake) — only the package changes.
3. **Decouple** — rewrite the use-case interface/impl signatures to take/return domain models and primitives, not DTOs. Move `actorId` resolution to the host. Move DTO↔domain mapping to the controller.
4. **Host integration** — update the controller to call the new signatures and own the mapping; update `build.gradle`/imports as needed.
5. **Verify** — `:ppm-core:compileJava`, `:ppm-svc:compileJava`, `:ppm-svc:test` (full suite, not just the touched aggregate) must all pass before moving to the next aggregate. Do not batch multiple aggregates into one unverified pass.

**One aggregate at a time.** Do not parallelize migrations across aggregates in a single pass — cross-aggregate references (e.g. `PlanPrice` referencing `Plan`) make partial migrations easy to get subtly wrong, and the verification step above is only meaningful if it isolates one aggregate's blast radius.

---

## Consequences

- Every aggregate migration is a bounded, verifiable unit of work with a fixed checklist — no per-aggregate re-litigation of package layout or port design.
- Renaming to the feature-first layout was done now, while only Plan had moved, specifically to avoid renaming the same classes twice (once now, once after all aggregates were migrated under the old layout).
- `ppm-svc`'s own `domain.model`/`domain.port`/`application.service`/`application.impl` packages remain populated by not-yet-migrated aggregates until each one goes through this checklist — this is expected intermediate state, not drift.
