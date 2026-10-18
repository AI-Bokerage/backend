# Contributing

## Before you start

Read `ARCHITECTURE_CERTIFICATION.md` and `DEVELOPER_GUIDE.md` first. This
repository enforces its architecture with a failing test
(`bsm-core`'s `ArchitectureTest`, built on ArchUnit), not just documentation
— code that violates the module boundaries below will not compile a green
build, regardless of how correct the business logic is.

## Module boundaries (non-negotiable)

- `bsm-core` must never import Spring MVC, Spring Security, JPA/Hibernate,
  AMQP/Kafka client classes, Resilience4j, or any payment-provider SDK
  (Stripe, Razorpay). It must never import a `bsm-svc`-layer package.
  `ArchitectureTest` enforces this at build time.
- `bsm-core` communicates with the outside world only through the ports in
  `domain/port` (plus `PaymentGatewayResolver` in `application/service`).
  Never add a direct dependency from a `bsm-core` application service onto
  a concrete infrastructure class.
- `bsm-spring-boot-starter` may depend on Spring Boot's autoconfigure
  machinery, but never on JPA/AMQP/payment-provider SDKs directly — its job
  is wiring, not implementing.
- `bsm-svc` is the only module allowed to depend on real infrastructure
  (Postgres, Redis, RabbitMQ, Stripe, Razorpay, S3, etc.) and on
  `libs/java-common`. It must never be depended on by `bsm-core`,
  `bsm-spring-boot-starter`, or `bsm-demo`.
- `bsm-demo` may only depend on `bsm-spring-boot-starter` and
  `libs/security-spi` — never on `bsm-svc` or `libs/java-common`. This is
  what keeps it a credible second-consumer portability proof; do not add a
  `bsm-demo` dependency on anything host-specific without a strong reason
  and a note in `SECOND_CONSUMER_VALIDATION.md`.

## Adding a new aggregate or port

See `DEVELOPER_GUIDE.md`'s "Adding a new aggregate" and "Adding a new port"
sections for the step-by-step process, including where the application
service, port interfaces, domain model, and (if it should be
auto-configured) the starter's `@AutoConfiguration` class and
`BsmAggregateSpec` entry all belong.

## Testing expectations

- New application-service logic needs unit tests with mocked ports
  (Mockito), matching the existing style in `bsm-core/src/test`.
- Any change to a `domain/port` interface's method signatures is a breaking
  change for every implementer (bsm-svc adapters, bsm-demo adapters, and any
  external consumer) — flag this explicitly in your PR description; see
  `VERSIONING.md` for what counts as breaking.
- If your change affects auto-configuration behavior, add or update an
  `ApplicationContextRunner` test in
  `bsm-spring-boot-starter/src/test/.../BsmAutoConfigurationTest.java`.
- Run `./gradlew build` before opening a PR — this runs every module's
  tests plus `ArchitectureTest` plus (where applicable)
  `publishToMavenLocal` validation for the affected module.

## What NOT to do

- Don't add a new module without discussing it first — six modules already
  have clearly separated responsibilities (see `DEPENDENCY_AUDIT.md`'s
  repository audit section); a seventh module needs a real reason.
- Don't add a speculative extension point (a port nobody implements yet) —
  see `TECHNICAL_DEBT_REGISTER.md`'s note on `BsmAuthorizationPort`, which
  already has this problem and should be resolved, not imitated.
- Don't weaken `ArchitectureTest` to make a change compile. If your change
  genuinely needs a new architectural rule, propose changing the rule
  itself, in its own PR, with a documented rationale.

## Commit / PR conventions

Follow whatever commit-message and PR-template conventions your
organization already uses elsewhere in this monorepo. There is nothing BSM-
specific to add here beyond the architectural rules above.
