# Invoice Engine (`fin-invoice`)

A reusable financial-invoice domain model — no persistence, no REST, no Spring, no PDF, no payment
gateway, and no application-specific concepts (Project, Tenant, Customer, Order, Subscription, ...).
Applications adapt their own domain to this engine via the reference and metadata systems, not the
other way around. Depends on `fin-api` (shared kernel) and `fin-money` (Money Engine).

## Package map

```
io.genfin.invoice
├── invoice/        Invoice (aggregate root) + InvoiceBuilder, InvoiceType, InvoiceDates, AuditInfo
├── line/           InvoiceLine (self-calculating) + InvoiceLineBuilder
├── id/             InvoiceId, LineId, DocumentId — strongly-typed, never raw UUIDs
├── reference/      Reference/ReferenceType/ReferenceValue/ReferenceCollection — the CPMS-free link model
├── metadata/       TypedValue, CustomField, Metadata, Attributes, ExtensionProperties
├── lifecycle/      InvoiceState/InvoiceEvent + the standard transition table (fin-api StateMachine)
├── discount/       Discount model + DiscountEngine
├── adjustment/     Adjustment model + AdjustmentEngine
├── attachment/      DocumentReference/AttachmentReference — pointers only, no file storage
├── event/          InvoiceCreated/Issued/Viewed/Cancelled/Paid/Voided/Overdue/Closed/Updated
├── numbering/      InvoiceNumber + the numbering SPI (sequential/timestamp/UUID/custom)
├── calculation/    InvoiceCalculationPolicy/Context/Result — Invoice never computes its own totals
├── validation/     ValidationRule/Context/Result + 8 default rules
├── builder/        AdjustmentBuilder, CopyBuilder, CloneBuilder, RevisionBuilder
├── factory/        InvoiceFactory, LineFactory, AdjustmentFactory, DiscountFactory, ReferenceFactory, MetadataFactory
├── config/         InvoiceConfiguration (immutable) + InvoiceConfigurations
├── serialization/  ReferenceCodec (see scope note below)
├── spi/            InvoiceExtensions — bootstraps defaults into a fin-api ExtensionRegistry
├── exception/      InvoiceErrorCode + exception hierarchy
└── port/<feature>/ The pluggable side of every feature above (interfaces only)
    internal/<feature>/ Hidden default implementations (never exported by module-info)
```

## Invoice lifecycle

States: `DRAFT → ISSUED → SENT → VIEWED`, with `PARTIALLY_PAID`/`PAID`/`OVERDUE` reachable from any
issued state, and `CANCELLED`/`VOIDED`/`REFUNDED` as terminal exits. The whole table lives in
`DefaultLifecycleProvider` and is built entirely on `fin-api`'s generic `StateMachine` — nothing about
transitions is hardcoded in `Invoice` itself. Swap `LifecycleProvider` to add custom states/events
without touching `Invoice`.

Structural edits (`addLine`, `addDiscount`) are only permitted while `DRAFT`. `addAdjustment` and
payments are allowed any time before a terminal state. `close()` requires a terminal state first —
"closed" is a separate administrative flag, not a lifecycle state, since an invoice can be
paid/voided/cancelled/refunded and closed independently of which of those it was.

## Calculation flow

`Invoice` never sums its own totals — `InvoiceCalculator.calculate(invoice, context)` does, external
to the aggregate (same principle as `Money` delegating to `MoneyCalculator`, one level up). The
default calculator: sums each line's `netAmount()` (which already applied its own line-level
discount) for the subtotal; applies header-level discounts **sequentially** against the shrinking
remainder (so two large percentage discounts can never combine past 100%); folds in adjustments
(sign carries direction: credits are negative, fees/penalties/surcharges positive); and totals
line-level tax. `balanceDue = grandTotal - amountPaid`.

## Reference model

The engine never knows what a reference means. `ReferenceType` is just a code
(`StandardReferenceType.of("PROJECT")`, `"SUBSCRIPTION"`, `"PURCHASE_ORDER"`, anything an application
needs); `Reference` pairs a type with an opaque `ReferenceValue` and an optional display label;
`ReferenceCollection` holds them, queryable by type. A `ReferenceResolver` extension point lets an
application supply human-readable labels without the engine interpreting the reference itself.

## Metadata system

Instead of hundreds of optional fields: `Metadata` is an ordered set of `CustomField`s, each a
`TypedValue` (`STRING`/`NUMBER`/`BOOLEAN`/`INSTANT`, with type-checked accessors). `Attributes` is a
lighter flat string-tag bag. `ExtensionProperties` namespaces `Metadata` bags per plugin/integration
so unrelated extensions never collide on a field name.

## Builder usage

```java
Invoice invoice = InvoiceBuilder.newInvoice()
    .currency(usd)
    .dueDate(dueInstant)
    .actor("system")
    .build();

invoice.addLine(LineFactory.simple("Consulting", BigDecimal.ONE, Money.of("500.00", usd)), clock, "system");
invoice.addDiscount(DiscountFactory.percentage(Percentage.ofPercent(new BigDecimal("10")), "loyalty"), clock, "system");
invoice.issue(InvoiceNumberGenerators.sequential(NumberTemplate.of("INV-{YEAR}-{SEQ}")).generate(context), clock, "system");

Invoice revision = RevisionBuilder.reviseOf(invoice, StandardReferenceType.of("REVISION_OF"), clock, "system");
```

`CopyBuilder` starts a blank draft with the same header (currency/type/due date); `CloneBuilder`
additionally duplicates lines/discounts/tags/references/metadata into a fresh identity; `RevisionBuilder`
does what `CloneBuilder` does and links the new invoice back to the original via a `Reference`.

## Number generation

`InvoiceNumberGenerator` is the SPI; three built-ins ship (`sequential` — template + `SequenceProvider`
+ `NumberFormatter`, partitioned by an opaque `scopeKey` for tenant/region isolation; `timestamp`;
`uuid`), none of them privileged — a bank's accounting-system format or an ERP's region-specific
scheme is just another `InvoiceNumberGenerator` implementation registered the same way.

## Extension model

Every pluggable behavior is discoverable through the `fin-api` `ExtensionRegistry`
(`InvoiceExtensions.registerDefaults(registry)` wires in every built-in default — no
`switch`/`instanceof` anywhere):

| Extension point | Purpose |
|---|---|
| `LifecycleProvider` | The whole state/transition table |
| `InvoiceCalculator` | Every total |
| `InvoiceValidator` | Structural and business rule checks |
| `InvoiceNumberGenerator` | Number generation scheme |
| `DiscountEngine` / `AdjustmentEngine` | How a discount/adjustment applies to a base amount |
| `InvoiceFormatter` | Display rendering |
| `ReferenceResolver` | Human-readable labels for opaque references |
| `MetadataResolver` | Metadata field lookup indirection |
| `InvoiceBuilderProvider` | Organization-default pre-configured builder |

## Serialization — scope note

Only `ReferenceCodec` is provided. This phase is explicitly persistence-free, and `Invoice` is a live
aggregate whose lifecycle `StateMachine` isn't meaningfully serializable (only replayable via its
domain events) — a full aggregate snapshot codec belongs with whichever future module owns
persistence, composing `ReferenceCodec` and the `Money`/`Currency` codecs from `fin-money` rather than
reinventing them.

## Example: end-to-end

```java
ExtensionRegistry registry = ExtensionRegistries.create();
MoneyExtensions.registerDefaults(registry);
InvoiceExtensions.registerDefaults(registry);

Invoice invoice = InvoiceFactory.using(registry).newDraft(usd, dueInstant, "system").build();
invoice.addLine(LineFactory.simple("Widget", BigDecimal.TEN, Money.of("9.99", usd)), clock, "system");

ValidationResult validation = InvoiceValidators.standard().validate(invoice, ValidationContext.at(Instant.now()));
if (validation.isValid()) {
    invoice.issue(InvoiceFactory.using(registry).nextNumber(numberContext), clock, "system");
}

InvoiceCalculationResult totals = InvoiceCalculators.standard()
    .calculate(invoice, InvoiceCalculationContext.standard(Instant.now()));
```
