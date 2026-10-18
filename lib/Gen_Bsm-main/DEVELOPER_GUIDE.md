# Developer Guide

For anyone contributing to `bsm-core`, `bsm-spring-boot-starter`,
`bsm-svc`, or `bsm-demo`. Read `ARCHITECTURE_CERTIFICATION.md` first for the
full module dependency graph and ownership rules; this guide is the
practical "how do I actually add X" companion to that document.

## Module responsibilities (quick reference)

| Module | Responsibility |
|---|---|
| `bsm-core` | Framework-agnostic domain + application services + ports. The library. |
| `bsm-spring-boot-starter` | Auto-configuration wiring bsm-core into a Spring Boot context. |
| `bsm-svc` | Host application: real adapters, REST controllers, migrations. |
| `bsm-demo` | Independent second-consumer app proving portability. |
| `libs/security-spi` | Pure-Java security interface contracts, zero deps. |
| `libs/java-common` | CPMS-platform-shared cross-cutting concerns, host-only. |

Full detail in `DEPENDENCY_AUDIT.md`'s repository audit section.

## Package conventions in `bsm-core`

```
com.company.bsmsvc.domain.model       — domain entities (Subscription, PlatformInvoice, ...)
com.company.bsmsvc.domain.enums       — domain enums (SubscriptionStatus, BillingCycle, ...)
com.company.bsmsvc.domain.event       — domain events
com.company.bsmsvc.domain.exception   — domain exceptions (ConcurrentUpdateException, ...)
com.company.bsmsvc.domain.port        — extension SPIs (the 40 ports)
com.company.bsmsvc.domain.service     — pure domain logic with no port dependencies
com.company.bsmsvc.application.service — public application service interfaces (Public API tier)
com.company.bsmsvc.application.impl   — application service implementations (Internal tier)
com.company.bsmsvc.application.util   — shared application-layer helpers
```

A class's package tells you its stability tier by convention:
`application.service` interfaces and `domain.port` interfaces are the
stable, documented surface (see `PUBLIC_API.md`); `application.impl` and
anything not re-exported through those two packages is internal and can
change without notice.

## Adding a new port

1. Define the interface in `bsm-core/src/main/java/.../domain/port/`. Name
   it `XxxPort` (repository ports) or a descriptive service-style name if
   it's not a repository (e.g. `DunningEventPublisher`,
   `PaymentGatewayResolver` — note the latter lives in `application.service`
   for historical reasons, not `domain.port`; new ports should go in
   `domain.port` unless there's a strong reason not to).
2. Add Javadoc covering purpose, thread-safety expectations if they differ
   from the default (stateless/thread-safe — see `PORT_REFERENCE.md`), and
   any lifecycle notes.
3. Add a real adapter in `bsm-svc` implementing it.
4. Add (or note the absence of) an in-memory reference adapter in
   `bsm-demo`, for the portability proof.
5. If it should be part of an auto-configured aggregate, add it to the
   relevant `@ConditionalOnBean` list in `bsm-spring-boot-starter`'s
   `@AutoConfiguration` class and to `BsmAggregateSpec`'s required-port set
   for that aggregate — see `BsmPortAvailabilityValidator` and
   `BsmAutoConfigurationTest` for how the fail-fast validation is wired and
   tested.
6. Update `PORT_REFERENCE.md` and `PUBLIC_API.md` in the same PR — these
   are audit documents that must reflect reality, not aspirational future
   state.
7. Adding a method to an existing port is a breaking change — see
   `VERSIONING.md`.

## Adding a new aggregate

1. Design the application service interface(s) in `application.service`,
   the ports it needs in `domain.port`, and any new domain model/enum/event
   types it introduces.
2. Implement the service in `application.impl`, with unit tests mocking its
   ports (Mockito), matching existing test style.
3. If the aggregate should be starter-auto-configured: create a new
   `@AutoConfiguration` class in `bsm-spring-boot-starter`, following the
   pattern in the nine existing ones (`@AutoConfigureAfter` ordering
   relative to any aggregate it depends on, `@ConditionalOnProperty(prefix
   = "bsm", name = "enabled", matchIfMissing = true)`, and a
   `@ConditionalOnBean` list covering every port the aggregate's service
   constructor needs). Add the aggregate to `BsmAggregateSpec` so
   `BsmPortAvailabilityValidator` can fail fast on partial configuration.
4. Add `ApplicationContextRunner` tests to `BsmAutoConfigurationTest`
   covering: full port set registers the service, missing anchor port
   skips registration silently, partial port set fails fast with an
   actionable message.
5. Implement real adapters in `bsm-svc`; consider in-memory adapters in
   `bsm-demo` if the aggregate is exercisable in the demo workflow.
6. Update `ARCHITECTURE_CERTIFICATION.md`'s port/adapter table,
   `PUBLIC_API.md`, `PORT_REFERENCE.md`, and (if it adds configuration)
   `CONFIGURATION_REFERENCE.md`.

## Testing strategy

- **Unit tests** (`bsm-core/src/test`): application services tested with
  mocked ports (Mockito + AssertJ). This is the majority of test coverage
  and should stay fast and dependency-free.
- **Architecture tests** (`bsm-core`'s `ArchitectureTest`, ArchUnit): a
  failing build, not a linter warning, if `bsm-core` imports anything from
  the forbidden list (Spring MVC/Security/JPA/AMQP/Kafka/Resilience4j,
  payment SDKs, host-layer packages). Run automatically as part of
  `bsm-core`'s test task.
- **Starter integration tests** (`BsmAutoConfigurationTest`,
  `ApplicationContextRunner`): verify auto-configuration behavior in
  isolation, without booting a full application — full-port-set,
  missing-anchor-port, and partial-port-set-fails-fast scenarios per
  aggregate.
- **`bsm-svc` integration tests**: Testcontainers-backed tests against real
  Postgres/RabbitMQ for adapters that need to prove real infrastructure
  behavior (optimistic locking, message delivery, etc.).
- **`bsm-demo` end-to-end test**: one full business workflow (tenant
  onboarding → subscription → invoice → payment → dunning, or similar)
  exercised entirely through in-memory adapters, proving the library works
  standalone.

## Architectural rules future contributors must follow

See `CONTRIBUTING.md`'s "Module boundaries (non-negotiable)" section for
the enforceable rules, and `ARCHITECTURE_CERTIFICATION.md` for the full
rationale and dependency graph. In short: `bsm-core` stays framework-
agnostic and infrastructure-free; consumers of `bsm-core` — including
`bsm-svc` itself — talk to it only through ports and public application
service interfaces, never by reaching into `application.impl`.
