# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added

- `fin-api` shared kernel: exceptions/error codes, `Result`, strongly-typed identifiers, `ClockProvider`, validation, generic state machine, immutable configuration, domain events, value-object base classes, utilities, serialization contracts, version metadata, and SPI/`ExtensionRegistry` infrastructure.
- `fin-money` Money Engine: `Money`/`Currency` value objects, arithmetic engine (`MoneyCalculator`/`ArithmeticPolicy`), precision/scale/rounding policies, currency conversion SPI, lossless allocation (largest-remainder method), percentage engine (discount/markup/margin/compound), formatting/parsing, `Money`/`Currency`/`TaxMetadata` codecs, immutable `MoneyConfiguration`, factory layer, and a jurisdiction-agnostic tax extension model (`TaxContext`/`TaxBreakdown`/`TaxCalculator` SPI — zero tax logic, ready for Phase 4).
- `fin-invoice` Invoice Engine: `Invoice` aggregate (built on `fin-api`'s `AggregateRoot`/`StateMachine`) with a fully pluggable lifecycle, `InvoiceLine`, a CPMS-free reference model (`Reference`/`ReferenceType`/`ReferenceCollection`), a structured metadata system (`TypedValue`/`Metadata`/`Attributes`/`ExtensionProperties`), discount/adjustment models, an external `InvoiceCalculator` engine, a configurable `InvoiceValidator` with 8 default rules, a fully pluggable numbering SPI (sequential/timestamp/UUID), builders (including copy/clone/revision), factories, `ReferenceCodec` serialization, and `InvoiceExtensions` `ExtensionRegistry` bootstrap.
- `fin-payment` Payment Engine: `Payment` aggregate (built on `fin-api`'s `AggregateRoot`/`StateMachine`) with a fully pluggable 14-state lifecycle, provider-neutral `PaymentIntent`/`PaymentSession`/`PaymentAttempt` models, an `Authorization`/`Capture`/`Void`/`Release` model supporting multiple partial captures, a generic provider-agnostic `PaymentGateway` SPI (the integration point for future `fin-stripe`/`fin-razorpay` modules), a generic `FailureReason` model, an external `PaymentCalculator` (balance/outstanding/settlement/allocation covering over/underpayment), a configurable `PaymentValidator` with 7 default rules, a storage-free `IdempotencyValidator`, builders, `ReferenceCodec` serialization, and `PaymentExtensions` `ExtensionRegistry` bootstrap. Deliberately does not depend on `fin-invoice` — invoice linkage is via its own self-contained `Reference` model.
- `fin-provider-api` Provider Platform: provider-neutral gateway infrastructure — `ProviderDescriptor`/registry/selector/resolver/loader, `CapabilitySet` negotiation, `Credential`/`AuthenticationScheme` model, a full webhook pipeline (`WebhookVerifier`/`WebhookParser`/`WebhookProcessor`/`WebhookReplayProtection`), retry classification and backoff strategies, health monitoring, a `CircuitBreaker` abstraction, token-bucket rate limiting, provider idempotency mapping, tokenization/payment-link/checkout contracts, provider domain events, `ProviderConfiguration`, and `ProviderApiExtensions` `ExtensionRegistry` bootstrap. Published a shared `PaymentGatewayComplianceTest` (via `java-test-fixtures`) that runs identically against every provider implementation. Does not modify any existing `fin-payment` public API.
- `fin-stripe` and `fin-razorpay`: reference `PaymentGateway` implementations against the official Stripe (`stripe-java` 33.2.0) and Razorpay (`razorpay-java` 1.4.10) SDKs — request/response mapping, provider-specific `ErrorMapping`, webhook signature verification, and `ExtensionRegistry` bootstrap for each. Prove the Provider Platform is sufficient for a new provider to plug in without changing `fin-provider-api` or `fin-payment`. Both modules are deliberately non-modular (no `module-info.java`) since their SDKs pull in non-modularized transitive dependencies.

## [1.0.0-alpha1] - 2026-07-29

### Added

- Repository foundation: Gradle multi-module build (Kotlin DSL), version catalog, and `build-logic` convention plugins (`java-library`, `publishing`, `quality`, `testing`).
- Layered module structure: `fin-core`, `fin-providers`, `spring-starter`, `demo`, `bom`, `integration-tests`.
- Compile-ready, empty `api / port / internal` modules for `fin-api`, `fin-money`, `fin-tax`, `fin-invoice`, `fin-payment`, `fin-provider-api`, `fin-refund`, `fin-ledger`, `fin-pricing`, `fin-dunning`, `fin-document`, `fin-events`, `fin-stripe`, `fin-razorpay`.
- JPMS `module-info.java` for all `fin-core`/`fin-providers` modules.
- Spring Boot autoconfigure/starter scaffolding and runnable demo applications.
- Static analysis (Spotless, Checkstyle, PMD, Error Prone, JaCoCo, dependency analysis) wired into every module via convention plugins.
- Project documentation: README, CONTRIBUTING, this changelog.
