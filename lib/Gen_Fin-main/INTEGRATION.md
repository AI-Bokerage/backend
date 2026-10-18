# Integration Guide

How a consumer service (FIN-SVC or any other) wires the Money → Invoice → Payment → Refund →
Ledger → Reconciliation → Pricing → Dunning → Document chain, using only each module's published
public API — no `internal` package access, no reflection. This was verified end to end as part of
the V1 release readiness audit: **every link in this chain is buildable today with zero Gen-Fin
source changes.**

## The chain

### Money → Invoice — READY

`Money.of(amount, currency)` / `MoneyFactory.using(config).of(...)` are public factories. `Invoice`
itself has a package-private constructor, but `InvoiceFactory.using(registry).newDraft(currency,
dueDate, actor)` returns a public `InvoiceBuilder` whose `.build()` produces a real `Invoice` — no
internal access required. `InvoiceLine` is likewise built through the public `LineFactory`.

### Invoice → Payment — READY (by generic reference, deliberately)

`Payment` never references `Invoice` directly — its own javadoc states it links to an
invoice/customer/order only through a generic `Reference`. This is intentional decoupling, not an
oversight. The hooks a consumer uses:

- `Invoice.recordPayment(Money amount, Money outstandingTotal, ClockProvider, String actor)` —
  called after computing a payment amount.
- `Invoice.addReference(Reference, ...)` (`fin-invoice`'s own `Reference` type) — records the
  payment's id against the invoice.
- `Payment.addReference(Reference.invoice(invoiceId), ...)` (`fin-payment`'s own, *separate*
  `Reference` type) — records the invoice's id against the payment.

**Each module maintains its own `Reference`/`ReferenceType` pair** (`invoice.reference.Reference`,
`payment.reference.Reference`, `refund.reference.Reference`, `dunning.reference.Reference`) —
deliberate per-module isolation, all public with `of(...)`/named factories (`Reference.invoice(id)`,
`Reference.payment(id)`). A consumer translates its own internal IDs into whichever module's
`Reference` type that module's API expects — a small, real amount of boilerplate, not a defect.

### Payment → Refund — READY (the simplest link)

`Refund` has a genuinely **public constructor**
(`public Refund(RefundId, RefundNumber, Money, RefundType, RefundDirection, Reference paymentReference)`)
that enforces the passed `Reference` be `StandardReferenceType.PAYMENT`.
`Reference.payment(String paymentId)` is the convenience factory. No factory-class indirection
needed at all.

### Payment/Refund → Ledger — READY (posting rules are yours to write)

`fin-ledger`'s normalized input is `FinancialFact(Money, Reference, FinancialFactType, OccurredAt,
Map<String,String> metadata)` — and its `Reference` type is **`fin-refund`'s** (`fin-ledger`
`requires transitive io.genfin.refund`, reusing that module's opaque type rather than inventing a
fourth copy). `Reference.payment(id)`/`Reference.invoice(id)` cover both origins.
`PostingEngine.post(FinancialFact, LedgerId, PostingContext)` is reachable via
`LedgerExtensions.registerDefaults(registry)`.

**Real caveat, not a blocker:** `PostingPolicy`/`PostingRuleRegistry` ship **empty** — you must
register your own posting-rule mapping (e.g. "invoice paid → debit Cash / credit AR") before the
Ledger does anything. See [LEDGER_ENGINE.md](LEDGER_ENGINE.md).

`fin-reconciliation`'s `ReconciliationItem.of(Reference, Money)` uses the identical
`refund.reference.Reference`, so reconciliation input is producible from Payment/Refund output
with zero extra glue.

### * → Pricing — READY (rules are yours to write)

`PricingRequest` has a public constructor and is fully decoupled from Invoice/Payment/Ledger by
design (its javadoc explicitly disclaims holding any of them). `PricingEngine.calculate(...)` is
reachable via `PricingExtensions.registerDefaults(registry)`. As with Ledger,
`CatalogRegistry`/`PricingPolicy`/`DiscountPolicy`/etc. ship **empty** — a consumer supplies real
catalog data and pricing strategies. See [PRICING_ENGINE.md](PRICING_ENGINE.md).

### * → Dunning — READY

`FinancialObligation` (public constructor + `FinancialObligationBuilder`) is the domain-agnostic
input, backed by **`fin-dunning`'s own**, fourth separate `Reference` type. `DunningCase` takes a
`FinancialObligation` directly via a public constructor. See [DUNNING_ENGINE.md](DUNNING_ENGINE.md).

### * → Document — READY (you write the mapper; the primitives are sufficient)

`fin-document` has **zero source-level awareness** of `fin-invoice` or any other business module —
no dependency, no `InvoiceDocumentMapper`. `DocumentMapper` (`io.genfin.document.port`) is a bare
port interface a consumer implements. The building blocks are genuinely sufficient for a
realistic invoice layout: `DocumentModel.of(metadata, sections, attributes)`,
`DocumentSection.of(title, elements)`, `TableElement.of(headers, rows)` (rows are
`List<List<String>>` — format `Money` via `fin-money`'s exported `format` package before
inserting), `KeyValueElement`, `TextBlockElement` — all public, all with real factories. Every
FIN-SVC-style consumer will independently write roughly the same Invoice→`DocumentModel` mapper;
that duplication is a real (mild) cost, not a blocker, and not something Gen-Fin needed to fix for
V1. See [DOCUMENT_ENGINE.md](DOCUMENT_ENGINE.md).

## Summary table

| Link | Verdict | Real caveat |
|---|---|---|
| Money → Invoice | READY | none |
| Invoice → Payment | READY | separate `Reference` types per module — translate IDs yourself |
| Payment → Refund | READY | none — simplest link in the chain |
| Payment/Refund → Ledger | READY | `PostingPolicy` ships empty — author your own posting rules |
| Payment/Refund → Reconciliation | READY | none — shares `fin-refund`'s `Reference` type |
| * → Pricing | READY | catalog/policies ship empty — author your own pricing rules |
| * → Dunning | READY | none |
| * → Document | READY | no invoice mapper shipped — write your own (primitives are sufficient) |

**No blockers found.** A FIN-SVC developer can wire this entire chain today using only published
Gen-Fin jars.

## See also

[ARCHITECTURE.md](ARCHITECTURE.md) for the layering conventions, [EXTENSIONS.md](EXTENSIONS.md)
for the `ExtensionRegistry` mechanics referenced throughout, [SPRING.md](SPRING.md) for automatic
wiring in a Spring Boot application, and [RELEASE_NOTES.md](RELEASE_NOTES.md) for the full V1
release readiness verdict.
