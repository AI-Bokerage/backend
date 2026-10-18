# Modules

Every module below follows the conventions in [ARCHITECTURE.md](ARCHITECTURE.md). This is the
confirmed dependency graph (every edge is Gradle `api(project(...))`, verified against every
`build.gradle.kts` and `module-info.java` as part of the V1 release audit — no cycles, no sibling
module importing another sibling's `internal` package, no core engine depending on
`fin-autoconfigure`/a provider/Spring):

```
fin-api                (no deps)
├── fin-money           → api
│   ├── fin-invoice       → api, money
│   ├── fin-payment       → api, money
│   │   ├── fin-refund      → api, money, payment
│   │   │   └── fin-reconciliation → api, money, payment, refund
│   │   │       └── fin-ledger       → api, money, payment, refund, reconciliation
│   │   └── fin-provider-api → api, money, payment
│   │       ├── fin-stripe    → provider-api (+ stripe-java SDK)
│   │       └── fin-razorpay  → provider-api (+ razorpay-java SDK)
│   └── fin-dunning       → api, money, payment
├── fin-pricing         → api, money
├── fin-document        → api (pdfbox is implementation-scoped, never api)
└── fin-tax             → api, money (Tax Engine V1 — Indian GST scenarios, see TAX_ENGINE.md)

fin-autoconfigure     → all 10 core modules (api) + spring-boot-autoconfigure;
                        fin-stripe/fin-razorpay/actuator are compileOnly
fin-spring-boot-starter → fin-autoconfigure only

fin-events            → empty stub module, unscoped by any phase to date
```

## Core modules (`fin-core/`)

| Module | Purpose | Doc |
|---|---|---|
| `fin-api` | Shared kernel: `ExtensionRegistry`, `GenFinException`/`ErrorCode`, `Validate`, `Identifier`, `Extension` marker, `StateMachine` | (no dedicated doc — foundational, described in [ARCHITECTURE.md](ARCHITECTURE.md)) |
| `fin-money` | Money/Currency, arithmetic, rounding, allocation, formatting, tax-ready-but-tax-free extension points | [MONEY_ENGINE.md](MONEY_ENGINE.md) |
| `fin-tax` | Tax Engine V1 — Indian GST resolution/calculation, integrates into `fin-money`'s `TaxCalculator` port | [TAX_ENGINE.md](TAX_ENGINE.md) |
| `fin-invoice` | Invoice lifecycle, calculation, numbering, references, metadata | [INVOICE_ENGINE.md](INVOICE_ENGINE.md) |
| `fin-payment` | Provider-agnostic payment domain, gateway SPI, idempotency contracts | [PAYMENT_ENGINE.md](PAYMENT_ENGINE.md) |
| `fin-provider-api` | Provider-neutral infrastructure (discovery, retry, circuit breaker, webhooks) that any payment provider plugs into | [PROVIDER_GUIDE.md](PROVIDER_GUIDE.md) |
| `fin-refund` | Refund lifecycle, reusing `fin-payment`'s gateway | [REFUND_ENGINE.md](REFUND_ENGINE.md) |
| `fin-reconciliation` | Matching, comparison, discrepancy detection, tolerance | [RECONCILIATION_ENGINE.md](RECONCILIATION_ENGINE.md) |
| `fin-ledger` | Chart of Accounts, double-entry posting, balances, periods, trial balance | [LEDGER_ENGINE.md](LEDGER_ENGINE.md) |
| `fin-pricing` | Catalog, discount/promotion/coupon/credit engines, pricing pipeline | [PRICING_ENGINE.md](PRICING_ENGINE.md) |
| `fin-dunning` | Retry/backoff, reminders, escalation, collection rules, over a generic `FinancialObligation` | [DUNNING_ENGINE.md](DUNNING_ENGINE.md) |
| `fin-document` | Templates, branding, letterhead, QR, layout, PDF rendering, preview, validation, events | [DOCUMENT_ENGINE.md](DOCUMENT_ENGINE.md) |
| `fin-events` | Empty stub — never scoped by any phase to date | — |

## Provider modules (`fin-providers/`)

| Module | Purpose |
|---|---|
| `fin-stripe` | Production-ready `PaymentGateway` implementation wrapping `stripe-java` |
| `fin-razorpay` | Production-ready `PaymentGateway` implementation wrapping `razorpay-java` |

Both audited for production readiness (configuration, capture modes, retries, idempotency,
webhook verification, error mapping, refunds) — see [PROVIDER_GUIDE.md](PROVIDER_GUIDE.md)'s
"Production readiness" section for the two provider-specific facts worth knowing before going
live.

## Spring integration (`spring-starter/`)

| Module | Purpose |
|---|---|
| `fin-autoconfigure` | One `@AutoConfiguration` class per core module, populating a shared `ExtensionRegistry`, `@ConditionalOnMissingBean` everywhere |
| `fin-spring-boot-starter` | Dependency-aggregating starter — `implementation("io.genfin:fin-spring-boot-starter")` and every core module is on the classpath |

See [SPRING.md](SPRING.md).

## Other

| Path | Purpose |
|---|---|
| `demo/` | Runnable sample applications (`demo-invoice` is the complete, end-to-end one — see [SPRING.md](SPRING.md)) |
| `bom/` | `java-platform` BOM constraining every module's version for consumers |
| `integration-tests/` | Cross-module integration tests |
| `build-logic/` | Gradle convention plugins shared by every module |
