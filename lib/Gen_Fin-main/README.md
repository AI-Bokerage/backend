# Gen-Fin

A modular, business-domain-agnostic finance framework for Java/Spring Boot applications — built with the same philosophy as Spring Security, Spring Data, Hibernate, or Flyway: a reusable platform, not a single application.

## Vision

Payments, invoicing, tax, pricing, refunds, and dunning are solved problems that every SaaS product ends up reinventing. Gen-Fin exists to be the reusable finance layer any Java/Spring Boot application can embed, independent of any single consumer's business domain. CPMS FIN-SVC is one future consumer of this framework, not part of it.

## Status

**V1 — feature complete, release-readiness audited.** Money, Invoice, Payment, Refund,
Reconciliation, Ledger, Pricing, Dunning, Document, and a scoped Tax Engine V1 (Indian GST — see
[TAX_ENGINE.md](TAX_ENGINE.md)) are all implemented, along with production-ready Stripe and
Razorpay provider integrations and a Spring Boot starter. Full tax compliance (filing, E-Invoicing,
other jurisdictions) is deferred to V2 — see [RELEASE_NOTES.md](RELEASE_NOTES.md) for the full
release readiness report and the complete V2 backlog.

## Module Overview

See [MODULES.md](MODULES.md) for the full module-by-module description and dependency graph.

```
fin-core/          Business-domain-agnostic core libraries
  fin-api           Shared kernel: ExtensionRegistry, exceptions, validation, identifiers
  fin-money         Money / currency engine
  fin-tax           Tax Engine V1 (Indian GST scenarios only — see TAX_ENGINE.md)
  fin-invoice       Invoice engine
  fin-payment       Payment engine + gateway SPI
  fin-provider-api  Payment provider platform (implemented by fin-providers/*)
  fin-refund        Refund engine
  fin-reconciliation Reconciliation engine
  fin-ledger        Ledger / accounting engine
  fin-pricing       Pricing engine
  fin-dunning       Dunning (retry/collections) engine
  fin-document      Document engine (templates, branding, PDF rendering, ...)
  fin-events        Empty stub — not yet scoped by any phase

fin-providers/     Payment provider integrations (implement fin-provider-api)
  fin-stripe        Production-ready
  fin-razorpay      Production-ready

spring-starter/    Spring Boot integration — see SPRING.md
  fin-autoconfigure         Auto-configuration for all 10 core modules
  fin-spring-boot-starter   Dependency-aggregating starter

demo/              Runnable sample applications (demo-invoice is the complete end-to-end example)
bom/               Dependency-management BOM for consumers
integration-tests/ Cross-module integration tests
```

## Architecture

- **Gradle multi-module build**, Kotlin DSL, driven by convention plugins in `build-logic/` (no duplicated configuration per module).
- **`api / port / internal` package separation** in every module: `api` is the only public surface, `port` holds extension points (SPI), `internal` holds hidden implementations. Consumers must never import from `internal`.
- **JPMS (`module-info.java`)** in every `fin-core`/`fin-providers` module enforces these boundaries at compile time. Spring-facing modules (`spring-starter`, `demo`) are intentionally non-modular, since Spring Boot's classpath scanning does not compose with the module system.
- **Single package namespace**: `io.genfin`.
- Framework foundation stays free of Spring Web/MVC/Data, Hibernate/JPA, provider SDKs, and messaging/cache clients — those belong to integration modules, not the core.

## Versioning

[Semantic Versioning](https://semver.org/). Current version: `1.0.0-alpha1`.

## Getting Started

```bash
./gradlew build
```

For a Spring Boot application, add `implementation("io.genfin:fin-spring-boot-starter")` and
start — see [SPRING.md](SPRING.md) for the full quick start, or run the working example:

```bash
./gradlew :demo-invoice:bootRun
```

For a plain-Java application, see [EXTENSIONS.md](EXTENSIONS.md) for how to bootstrap an
`ExtensionRegistry` yourself.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — repo-wide conventions (package layering, the extension
  mechanism, exceptions, JPMS)
- [MODULES.md](MODULES.md) — every module, its purpose, and the dependency graph
- [EXTENSIONS.md](EXTENSIONS.md) — the `ExtensionRegistry` SPI mechanism, plain-Java usage
- [INTEGRATION.md](INTEGRATION.md) — how a consumer service wires the whole engine chain together
- [SPRING.md](SPRING.md) — the Spring Boot starter
- [PROVIDER_GUIDE.md](PROVIDER_GUIDE.md) — adding/using Stripe, Razorpay, or a new provider
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — the V1 release readiness report and V2 backlog
- Per-engine docs: [MONEY_ENGINE.md](MONEY_ENGINE.md), [INVOICE_ENGINE.md](INVOICE_ENGINE.md),
  [PAYMENT_ENGINE.md](PAYMENT_ENGINE.md), [REFUND_ENGINE.md](REFUND_ENGINE.md),
  [RECONCILIATION_ENGINE.md](RECONCILIATION_ENGINE.md), [LEDGER_ENGINE.md](LEDGER_ENGINE.md),
  [PRICING_ENGINE.md](PRICING_ENGINE.md), [DUNNING_ENGINE.md](DUNNING_ENGINE.md),
  [DOCUMENT_ENGINE.md](DOCUMENT_ENGINE.md), [TAX_ENGINE.md](TAX_ENGINE.md)
