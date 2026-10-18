# Architecture

`ppm-core` is a hexagonal-architecture business library. This document explains the boundary between the library and its host, why the boundary is drawn where it is, and how the pieces fit together. For package-level conventions (where a new file goes), see [PACKAGE_GUIDE.md](PACKAGE_GUIDE.md). For how to actually consume this from a Spring Boot application, see [INTEGRATION_GUIDE.md](INTEGRATION_GUIDE.md).

## The shape: hexagonal / ports-and-adapters

```
                      ┌─────────────────────────────┐
                      │           HOST               │
                      │   (ppm-svc or any consumer)  │
                      │                               │
   HTTP request  ───▶ │  Controller                  │
                      │     │ (owns actorId + DTO↔domain mapping)
                      │     ▼                          │
                      │  ┌─────────────────────────┐  │
                      │  │        ppm-core          │  │
                      │  │                          │  │
                      │  │  UseCase (interface)     │  │
                      │  │       │                  │  │
                      │  │       ▼                  │  │
                      │  │  UseCaseImpl             │  │
                      │  │   ├─ Domain model         │  │
                      │  │   ├─ RepositoryPort ◀─────┼──┼── implemented by host
                      │  │   └─ DomainException      │  │   (JPA adapter, etc.)
                      │  │                          │  │
                      │  └─────────────────────────┘  │
                      │     │                          │
                      │     ▼                          │
                      │  JPA entity / Spring Data repo │
                      │  (implements RepositoryPort)   │
                      └─────────────────────────────┘
```

`ppm-core` owns the **inside** of the hexagon: domain models, use cases, and the outbound port interfaces they depend on. The host owns **every adapter** at the edges — inbound (REST, messaging) and outbound (persistence, external calls).

## What `ppm-core` owns

- **Domain models** — aggregates, entities, value objects, enums. No JPA, no Spring MVC, no Spring Security annotations. `Plan`, `PlanVersion`, `Module`, `Entitlement`, `PromoCode`, and their join aggregates (`PlanModule`, `PlanAddOn`, `PlanEntitlement`, `PromoCodePlan`) all live here.
- **Domain events** — `DomainEvent` subclasses, collected on `BaseEntity` and drained after a successful persist. Publishing the drained events (to RabbitMQ, an in-process bus, or nothing at all) is a host decision.
- **Outbound repository ports** — one interface per aggregate root (`PlanRepositoryPort`, `ModuleRepositoryPort`, ...). Interfaces only; `ppm-core` never implements them. Ports speak exclusively in domain model / primitive terms — no JPA entity, no DTO, ever crosses this boundary.
- **Use cases** — the inbound port in hexagonal terms. Each aggregate has a `<Aggregate>ApplicationService` interface plus its `Impl`. Use cases take domain models and primitives (`UUID`, `String`, enums) as parameters and return domain models — never host-specific request/response DTOs.
- **Domain exceptions** — `BusinessException` and its typed subclasses (`ResourceNotFoundException`, `AccessDeniedException`, `InvalidStateException`), each carrying an `ErrorCode`. Framework-independent; the host's exception handler maps them to HTTP status codes by type.

## What the host owns

- **Inbound adapters** — REST controllers, request/response DTOs, OpenAPI annotations, MapStruct DTO↔domain mappers.
- **Actor identity resolution** — extracting the acting user from `SecurityContextHolder` (or any other security stack) and passing it into use-case calls as an explicit `UUID actorId` parameter. Use cases never reach into a security context themselves.
- **Outbound adapters** — JPA entities, Spring Data repositories, and the adapter classes that implement each `RepositoryPort` by translating between the JPA entity and the domain model.
- **Messaging** — RabbitMQ/Kafka config, exchange/queue topology, publishers that drain and dispatch domain events.
- **Everything Spring Boot** — auto-configuration, `application.yaml`, actuator, scheduling, security filter chains.

## Why the actor-identity boundary matters

Early in the extraction, several use-case implementations called `SecurityUtils.requireCurrentUserId()` — a direct dependency on Spring Security's `SecurityContextHolder` — from inside what was meant to be framework-independent business logic. This wasn't a DTO-shape problem; it was a real coupling that would break for any host not using that exact security stack.

The fix, applied consistently across every migrated aggregate: use-case methods that need an acting user (create/update/delete) take `UUID actorId` as an explicit parameter. The host resolves the identity however it authenticates requests and passes it in. This is the one place in the extraction where "just move the file" wasn't enough — every aggregate's create/update/delete signature was rewritten to make this explicit.

## Why DTO↔domain mapping lives in the host, not the use case

Use cases originally accepted and returned host-specific `*Request`/`*Response` records (MapStruct-mapped). Moving those DTOs into `ppm-core` would have made the library depend on wire-format decisions (`@JsonInclude`, field naming, pagination envelopes) that are properly the host's concern — a second host with a different API shape would need to fork the use case, not just implement a port.

The fix: use cases take/return domain models and primitives only. The host's controller calls the use case, then maps the returned domain model to its own response DTO via its own MapStruct mapper. `PlanController` in `ppm-svc` is the reference example.

One deliberate exception: `PromoValidationService` returns `PromoValidationResult`, a plain `ppm-core` record with no Jackson annotations — mirroring the shape of the host's `PromoValidationResponse` DTO but without its `@JsonInclude(NON_NULL)` wire-serialization annotation. This was a genuine judgment call, documented in [docs/API_INVENTORY.md](docs/API_INVENTORY.md#promocode), because the host DTO is actively serialized by a live, tested controller and could not simply be moved as-is without either accepting a Jackson annotation inside `ppm-core` or splitting the type. The split was chosen to keep the "no serialization behavior in core" principle intact.

## Cross-aggregate composition: the Catalog read model

`CatalogQueryService` (in the `plan` package) is the one use case that reads across five aggregates (`Plan`, `PlanVersion`, `PlanModule`, `Module`, `PlanEntitlement`, `Entitlement`) to assemble a public-catalog read model. It lives in `ppm-core`, not the host, because the composition logic — which versions count as "latest," how modules and entitlements are joined per plan — is itself a business rule, not a presentation concern. Its five `Catalog*Response` records are plain domain read models (no `@JsonInclude`); at the time of writing no host controller consumes this service, so there is no live wire-format contract constraining them yet.

## Package convention

See [PACKAGE_GUIDE.md](PACKAGE_GUIDE.md) for the full feature-first layout (`<aggregate>/{model,port,usecase}`) and when a new subpackage is (and isn't) warranted.

## Forbidden dependencies

`ppm-core` must never depend on: Spring MVC/Web, Spring Security, RabbitMQ/Kafka clients, JPA/`EntityManager`, any persistence adapter, HTTP client libraries, or scheduling. See [docs/DEPENDENCY_AUDIT.md](docs/DEPENDENCY_AUDIT.md) for the full allowed list and the reasoning behind each entry, including the one accepted exception (`jackson-annotations`, used for wire-vocabulary enums like `PlanVisibility`).

## Governing ADRs

The architectural decisions above are recorded formally in:

- [ADR-001 — Plan → PlanModule → Module ownership](../ppm-svc/docs/adr/ADR-001-plan-module-ownership.md)
- [ADR-002 — ppm-core extraction pattern](../ppm-svc/docs/adr/ADR-002-ppm-core-extraction-pattern.md)

See the [ADR index](docs/adr/README.md) for the full list and how to add a new one.
