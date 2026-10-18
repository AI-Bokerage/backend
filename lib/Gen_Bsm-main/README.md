# BSM — Billing & Subscription Management Library

A reusable, framework-agnostic Java library for billing and subscription management, with an
optional Spring Boot starter. Extracted from a production Spring Boot monolith into a
hexagonal-architecture library that any JVM host application can adopt — bring your own
persistence, payment gateway, messaging, and authentication; the library owns the business logic.

[![Java](https://img.shields.io/badge/Java-21-orange)](COMPATIBILITY.md)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen)](COMPATIBILITY.md)
[![License](https://img.shields.io/badge/license-TBD-lightgrey)](LICENSE)
[![Build](https://img.shields.io/badge/build-passing-brightgreen)](RELEASE_PROCESS.md)

> **Status**: library extraction complete, targeting `1.0.0`. Artifacts are currently published
> to Maven Local only (`0.0.1-SNAPSHOT`) — see [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md) for what
> changes once a real `1.0.0` is cut, and [`LICENSE`](LICENSE) for the current (placeholder)
> licensing status.

---

## Table of contents

- [Why this library](#why-this-library)
- [Architecture](#architecture)
- [Modules](#modules)
- [Quick start](#quick-start)
- [Usage example](#usage-example)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Building from source](#building-from-source)
- [Testing](#testing)
- [Versioning & compatibility](#versioning--compatibility)
- [Security](#security)
- [Contributing](#contributing)
- [License](#license)

## Why this library

Billing and subscription logic — trial management, proration, dunning, invoice generation, plan
changes, add-ons — is business logic that shouldn't be re-written per project or locked into one
team's Spring Boot monolith. This library extracts that logic into `bsm-core`: zero dependency on
any specific database, message broker, payment provider SDK, or security framework, enforced at
build time by an ArchUnit test, not just by convention.

You plug in your own infrastructure by implementing plain Java interfaces ("ports"). If you're on
Spring Boot, `bsm-spring-boot-starter` auto-wires everything for you — implement the ports as
`@Component` beans and the library's application services register themselves.

**Proven portable**: an independent second-consumer application (`bsm-demo`) and a standalone
project entirely outside this repository, consuming only published Maven artifacts, both build a
working billing workflow using nothing but in-memory adapters. See
[`SECOND_CONSUMER_VALIDATION.md`](SECOND_CONSUMER_VALIDATION.md).

## Architecture

```
   Your Spring Boot app
          │
          ├── depends on ──▶ bsm-spring-boot-starter   (auto-configuration)
          │                          │
          │                          ▼
          │                     bsm-core                (domain + application services + ports)
          │                          ▲
          └── implements ────────────┘
              (your @Component adapters satisfy the ports —
               JPA, Redis, RabbitMQ, Stripe, whatever you use)
```

`bsm-core` has **zero** dependency on Spring MVC, Spring Security, JPA/Hibernate, AMQP/Kafka
clients, Resilience4j, or any payment-provider SDK — this is enforced by a failing ArchUnit test
(`bsm-core`'s `ArchitectureTest`), not just documented. Full rationale, module ownership rules, and
the complete port/adapter list live in
[`ARCHITECTURE_CERTIFICATION.md`](ARCHITECTURE_CERTIFICATION.md).

## Modules

| Module | Description | Published? |
|---|---|---|
| [`bsm-core`](bsm-core/) | Framework-agnostic domain models, application services, and 40+ port interfaces. The library. | Yes |
| [`bsm-spring-boot-starter`](bsm-spring-boot-starter/) | Auto-configuration wiring `bsm-core` into a Spring Boot context, with fail-fast validation for incomplete port sets. | Yes |
| [`bsm-svc`](bsm-svc/) | The reference host application — real JPA/Redis/RabbitMQ/Stripe/Razorpay/S3 adapters. Not a library; an example of a full production integration. | No |
| [`bsm-demo`](bsm-demo/) | An independent second-consumer application with in-memory adapters, proving the library is genuinely portable. | No |
| [`libs/security-spi`](libs/security-spi/) | Pure-Java security interface contracts, zero dependencies. | Yes |
| [`libs/java-common`](libs/java-common/) | Shared platform cross-cutting code used by `bsm-svc` only. | Yes |

Full responsibility/dependency breakdown in [`DEPENDENCY_AUDIT.md`](DEPENDENCY_AUDIT.md).

## Quick start

### Add the dependency

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("com.company:bsm-spring-boot-starter:1.0.0")
}
```

Maven:

```xml
<dependency>
    <groupId>com.company</groupId>
    <artifactId>bsm-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

> While only published to Maven Local, add `mavenLocal()` to your `repositories { }` block and use
> version `0.0.1-SNAPSHOT`. See [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md).

### Implement the ports you need

Each aggregate (subscription, invoice, payment, dunning, ...) auto-configures once its required
ports are present as Spring beans. Start with the smallest aggregate — **tenant-billing** needs
just two:

```java
@Component
public class JpaTenantBillingProfileRepository implements TenantBillingProfileRepositoryPort {
    // save(), findByTenantId() — your own persistence
}

@Component
public class MyTenantScopeAdapter implements TenantScopePort {
    // assertTenantAccess(), resolveEffectiveTenantId() — your own auth mechanism
}
```

Add more ports to unlock more aggregates (payment, invoice, subscription, dunning, ...) — see
[`STARTER_GUIDE.md`](STARTER_GUIDE.md) for the full aggregate→port table and
[`PORT_REFERENCE.md`](PORT_REFERENCE.md) for every port's purpose, requirements, and a
reference implementation to copy from.

### Run it

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

If you supply only *some* of an aggregate's required ports, startup fails fast with an actionable
error message listing exactly what's missing — see
[`STARTER_GUIDE.md`](STARTER_GUIDE.md#fail-fast-validation) for real examples of that message.

## Usage example

```java
@Autowired
private TenantBillingProfileService billingProfileService;

@Autowired
private SubscriptionService subscriptionService;

public void onboardTenant(UUID tenantId) {
    billingProfileService.createProfile(tenantId, PaymentProvider.STRIPE, "cus_123", "USD");

    Subscription subscription = subscriptionService.createSubscription(
        new CreateSubscriptionCommand(tenantId, planVersionId, BillingCycle.MONTHLY));
}
```

Ten worked, runnable examples (subscription lifecycle, invoicing, payment, renewal, dunning,
add-ons, plan changes) with real method signatures live in [`docs/examples/`](docs/examples/).

## Configuration

All configuration is optional with sensible defaults — nothing needs to be set for the library to
work out of the box:

```yaml
bsm:
  enabled: true                          # master switch
  trial:
    default-days: 14
  dunning:
    day1-retry-after-hours: 24
    suspend-after-days: 14
    cancel-after-days: 30
  reconciliation:
    threshold-seconds: 300
```

Full property reference (name, default, required/optional, description) in
[`CONFIGURATION_REFERENCE.md`](CONFIGURATION_REFERENCE.md).

## Documentation

| Doc | What it covers |
|---|---|
| [`GETTING_STARTED.md`](GETTING_STARTED.md) | First steps for a new consumer |
| [`STARTER_GUIDE.md`](STARTER_GUIDE.md) | Auto-configuration, aggregate/port table, fail-fast validation |
| [`INTEGRATION_GUIDE.md`](INTEGRATION_GUIDE.md) | Port-by-port implementation patterns (repository, payment gateway, events, security, pricing, scheduling, error handling, extension points) |
| [`API_STABILITY.md`](API_STABILITY.md) / [`PUBLIC_API.md`](PUBLIC_API.md) | Public API vs. SPI vs. internal vs. experimental tiers |
| [`PORT_REFERENCE.md`](PORT_REFERENCE.md) | Every port: purpose, adapters, required/optional, thread safety |
| [`CONFIGURATION_REFERENCE.md`](CONFIGURATION_REFERENCE.md) | Every configuration property |
| [`ARCHITECTURE_CERTIFICATION.md`](ARCHITECTURE_CERTIFICATION.md) | Module boundaries, dependency graph, architecture rules |
| [`SECOND_CONSUMER_VALIDATION.md`](SECOND_CONSUMER_VALIDATION.md) | Proof of portability (in-monorepo and published-artifact validation) |
| [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md) | Adding aggregates/ports, package conventions, testing strategy |
| [`DEPENDENCY_AUDIT.md`](DEPENDENCY_AUDIT.md) | Every module's dependencies, classified and justified |
| [`COMPATIBILITY.md`](COMPATIBILITY.md) | Supported Java/Spring Boot/Gradle/Maven versions |
| [`VERSIONING.md`](VERSIONING.md) | SemVer policy, what counts as breaking |
| [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md) | How releases are cut |
| [`CHANGELOG.md`](CHANGELOG.md) | Version history |
| [`ROADMAP.md`](ROADMAP.md) | Completed / planned / under consideration |
| [`TECHNICAL_DEBT_REGISTER.md`](TECHNICAL_DEBT_REGISTER.md) | Known gaps, why deferred, recommendation |
| [`docs/examples/`](docs/examples/) | Ten worked usage examples |

## Building from source

Requires Java 21 and Docker (for `bsm-svc`'s Testcontainers-based integration tests).

```bash
./gradlew clean build                    # compile + test every module
./gradlew :bsm-core:publishToMavenLocal :bsm-spring-boot-starter:publishToMavenLocal
```

## Testing

- **Unit tests** (`bsm-core`): application services tested with mocked ports.
- **Architecture tests** (`bsm-core`): ArchUnit-enforced module boundaries — a failing build, not a
  linter warning, if the boundary is violated.
- **Starter integration tests** (`bsm-spring-boot-starter`): `ApplicationContextRunner`-based tests
  covering full/partial/missing port configurations per aggregate.
- **Second-consumer validation** (`bsm-demo` + an outside-monorepo standalone project): an
  end-to-end billing workflow run through nothing but in-memory adapters, both via project
  dependency and via published Maven Local artifacts.

Full testing strategy in [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md#testing-strategy).

## Versioning & compatibility

Follows [Semantic Versioning](VERSIONING.md). `bsm-core` and `bsm-spring-boot-starter` release in
lockstep at the same version. See [`COMPATIBILITY.md`](COMPATIBILITY.md) for supported platform
versions and [`VERSIONING.md`](VERSIONING.md) for exactly what counts as a breaking change.

## Security

See [`SECURITY.md`](SECURITY.md) for the vulnerability disclosure process and security-relevant
design notes (tenant isolation, payment credential handling).

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for module boundary rules, how to add a new aggregate or
port, and testing expectations before opening a PR.

## License

See [`LICENSE`](LICENSE) — licensing is currently a placeholder pending an ownership decision; do
not redistribute published artifacts outside your organization until this is finalized.
