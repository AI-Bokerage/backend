# Gen-Fin V1 — Release Notes & Release Readiness Report

Version: `1.0.0-alpha1`. This document is the release-readiness gate for Gen-Fin V1, produced by a
full repository audit ahead of building the first production consumer (CPMS FIN-SVC). The audit's
governing question: **can a production-grade FIN-SVC be built entirely on top of Gen-Fin without
modifying the library?**

## What shipped in this hardening pass

- Six parallel read-only audits across dependency graph/JPMS/public API, the Spring Boot starter,
  the full engine-integration chain, Stripe/Razorpay provider readiness, the Document Engine, and
  developer experience/performance.
- **Fixed:** `fin-document`'s exceptions (`RenderException`, `BrandNotFoundException`,
  `LetterheadNotFoundException`, `QrCodeNotFoundException`, `RendererNotFoundException`,
  `TemplateNotFoundException`) now extend the shared `GenFinException` hierarchy via a new
  `DocumentErrorCode`, matching every other module's convention — it was the sole holdout.
- **Fixed — real correctness bug:** `StripeWebhookVerifier` previously HMACed the raw webhook body
  alone. Stripe's actual signature scheme signs `"<timestamp>.<rawBody>"` and ships a composite
  `Stripe-Signature` header (`t=...,v1=...`). The prior implementation would have rejected every
  genuine Stripe webhook in production. Fixed to parse the real header and verify the real scheme.
- Minor javadoc fix (`PaymentProperties`'s dangling `{@link}`).
- Full documentation pass: this file plus 15 others (see Documentation below).

## Repository Health: **PASS**

- Dependency graph: confirmed a clean DAG, no cycles, no sibling module importing another
  sibling's `internal` package, no core engine depending on `fin-autoconfigure`/a provider/Spring.
- JPMS: every `module-info.java` correctly scoped — no `internal` package ever exported, every
  `requires transitive` matches its Gradle `api` dependencies exactly.
- Gradle/version catalog/BOM/publishing: no duplicate or conflicting version pins; the BOM's
  constraint generation correctly includes the now-populated `fin-autoconfigure`/
  `fin-spring-boot-starter`/`fin-tax`. Minor cosmetic note: the BOM also lists `fin-events` as a
  constraint even though it's an empty stub module — not a build defect.

## Module Health

| Module | Verdict | Notes |
|---|---|---|
| `fin-api` | PASS | Shared kernel, no findings |
| `fin-money` | PASS | No findings |
| `fin-tax` | PASS | Tax Engine V1 (Indian GST scenarios) — see [TAX_ENGINE.md](TAX_ENGINE.md) |
| `fin-invoice` | PASS | No findings |
| `fin-payment` | PASS | No findings |
| `fin-provider-api` | PASS | No findings |
| `fin-refund` | PASS | No findings |
| `fin-reconciliation` | PASS | No findings |
| `fin-ledger` | PASS | Posting rules ship empty by design (documented, not a gap) |
| `fin-pricing` | PASS | Catalog/policies ship empty by design (documented, not a gap) |
| `fin-dunning` | PASS | No findings |
| `fin-document` | PASS (after fix) | Exception hierarchy fixed this pass; two documented known limitations (letterhead aspect ratio, annotation content) don't block FIN-SVC use |
| `fin-events` | PASS (empty, unscoped) | Never in scope for any phase to date |
| `fin-stripe` | PASS (after fix) | Webhook signature bug fixed this pass; otherwise fully production-ready |
| `fin-razorpay` | PASS | Fully production-ready; one documented idempotency limitation (SDK constraint, not a bug — see [PROVIDER_GUIDE.md](PROVIDER_GUIDE.md)) |
| `fin-autoconfigure` | PASS | No findings |
| `fin-spring-boot-starter` | PASS | No findings |

## Spring Boot Integration: **PASS**

Dependency aggregation, `AutoConfiguration.imports`, `@ConfigurationProperties` binding,
`@ConditionalOnMissingBean` coverage, provider/renderer bean discovery, and the demo application
were all re-verified fresh (not just recalled from earlier session context). `./gradlew
:fin-autoconfigure:check :fin-spring-boot-starter:build :demo-invoice:build` passes. Explicit
re-aggregation of the 10 core modules in `fin-spring-boot-starter` itself was evaluated and
rejected as pure duplication — `fin-autoconfigure` already declares them as `api`, which is
sufficient and matches the module's own design intent.

## Provider Readiness: **PASS**

Both Stripe and Razorpay are complete, non-stub implementations: configuration, capture modes,
retries, error mapping, and refunds all fully implemented for both. The one real bug found
(Stripe webhook signature scheme) is fixed in this pass. Razorpay's idempotency-key gap is a
genuine, deliberate SDK-level limitation, documented in [PROVIDER_GUIDE.md](PROVIDER_GUIDE.md) —
not something Gen-Fin can safely paper over without risking silent cross-request key leakage in
the underlying SDK.

## FIN-SVC Readiness: **PASS — no blockers**

**Can CPMS FIN-SVC now be implemented entirely on top of Gen-Fin without modifying the library?
Yes.** The full Money → Invoice → Payment → Refund → Ledger → Reconciliation → Pricing → Dunning →
Document chain was traced end to end using only each module's public API — no `internal` package
access required anywhere. Full detail in [INTEGRATION.md](INTEGRATION.md).

Three things a FIN-SVC developer should know going in — none of them are blockers, all are
documented:

1. Four modules (`fin-invoice`, `fin-payment`, `fin-refund`, `fin-dunning`) each maintain their
   own separate `Reference`/`ReferenceType` type rather than sharing one — a small, deliberate
   amount of ID-translation boilerplate is required when moving between them.
2. `fin-ledger`'s posting rules and `fin-pricing`'s catalog/policies ship empty by design — a
   FIN-SVC deployment must author its own before either engine is useful.
3. `fin-document` has no invoice-specific mapper — every consumer writes its own
   Invoice→`DocumentModel` mapping (all primitives needed to do so are public and sufficient).

## Documentation

Fifteen documents added or updated this pass, at repository root:
[README.md](README.md), [SPRING.md](SPRING.md), [ARCHITECTURE.md](ARCHITECTURE.md),
[MODULES.md](MODULES.md), [EXTENSIONS.md](EXTENSIONS.md), [INTEGRATION.md](INTEGRATION.md),
[PROVIDER_GUIDE.md](PROVIDER_GUIDE.md), [DOCUMENT_ENGINE.md](DOCUMENT_ENGINE.md),
[PAYMENT_ENGINE.md](PAYMENT_ENGINE.md), [INVOICE_ENGINE.md](INVOICE_ENGINE.md),
[PRICING_ENGINE.md](PRICING_ENGINE.md), [LEDGER_ENGINE.md](LEDGER_ENGINE.md),
[DUNNING_ENGINE.md](DUNNING_ENGINE.md), [REFUND_ENGINE.md](REFUND_ENGINE.md),
[RECONCILIATION_ENGINE.md](RECONCILIATION_ENGINE.md), [MONEY_ENGINE.md](MONEY_ENGINE.md), plus
this file. `MONEY_ENGINE.md`/`INVOICE_ENGINE.md`/`PAYMENT_ENGINE.md`/`PROVIDER_GUIDE.md` were
promoted from `docs/*.md` (renamed, de-staled) rather than duplicated; `DOCUMENT_ENGINE.md`/
`LEDGER_ENGINE.md`/`PRICING_ENGINE.md`/`DUNNING_ENGINE.md`/`RECONCILIATION_ENGINE.md`/
`REFUND_ENGINE.md` are thin root-level indexes cross-referencing each engine's canonical
module-root doc (`fin-core/fin-X/X.md`), which stays the authoritative deep-dive per this
repository's established convention.

## Remaining V2 items (intentionally postponed)

- **Tax Engine V2+** — `fin-tax` V1 solves only the Indian GST scenarios CPMS FIN-SVC needs today
  (CGST/SGST, IGST, LUT/IGST export, a stubbed import-service case). GST return filing, E-Invoicing/
  IRN, HSN/SAC lookups, Reverse Charge, TDS/TCS, and any non-Indian jurisdiction (VAT, US Sales Tax)
  are all deferred — see [TAX_ENGINE.md](TAX_ENGINE.md)'s Future Roadmap.
- **Additional payment providers** — PayPal, Adyen, Cashfree, PhonePe, PayU, Square, Braintree, or
  an internal banking API — the platform (`fin-provider-api`) is proven sufficient by two
  independent implementations; a third is pure addition, no platform change.
- **Additional document renderers** — DOCX, HTML, Excel, PowerPoint, a true image-primary-output
  renderer (see `DOCUMENT_ENGINE.md`'s Future Roadmap, inherited from `fin-document/DOCUMENT.md`).
- **Additional pricing strategies** — richer catalog/discount/promotion implementations; the
  extension points already exist, only concrete business rules are missing (by design).
- **Advanced reporting** — cross-engine reporting/analytics beyond each engine's own
  summary/report types.
- **Advanced observability** — metrics, distributed tracing, Micrometer/OpenTelemetry integration,
  custom Actuator endpoints beyond the one health indicator — explicitly out of scope for the
  Spring Boot starter phase.
- **fin-document renderer extensibility** — no public extension point to add a renderer to
  `DocumentRenderers.standard()` inside fin-document itself (the Spring starter's
  `SpringAwareRendererResolver` is a workaround, not a fix); a real fix is fin-document's own,
  separate follow-up if ever needed.
- **fin-events** — never scoped by any phase; purpose still undefined.
