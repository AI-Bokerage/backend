# Tax Engine V1 (`fin-tax`)

**This is Tax Engine V1, not a complete taxation framework.** It solves exactly the tax scenarios
CPMS FIN-SVC needs today (Indian GST — CGST/SGST, IGST, LUT/IGST export, a stubbed import-service
case, and a foreign↔foreign no-tax fallback) while staying structurally ready for a future
jurisdiction (UAE VAT, EU VAT, US Sales Tax) to plug in alongside India without any public API
change. It never becomes GST compliance software — no filing, no government integrations, no
E-Invoice/IRN, no HSN/SAC lookups, no Reverse Charge, no TDS/TCS. Those are Tax Engine V2+.

`fin-tax` never knows about Project, Invoice, Customer, Tenant, PMT, FIN-SVC, or CPMS — it only
understands tax inputs (`PartyTaxProfile`, `TransactionContext`) and produces tax outputs
(`TaxDecision`, then a `TaxBreakdown` via `fin-money`'s existing types). A consuming service adapts
its own domain objects into these shapes.

## Architecture: resolution → rate → calculation

```
Seller Profile + Buyer Profile + Transaction Context
        │
        ▼
TaxDecisionResolver   →  TaxDecision (which TaxTreatment applies, e.g. CGST_SGST)
        │
        ▼
TaxRateProvider        →  TaxRate (the configured rate for each component the treatment needs)
        │
        ▼
IndianTaxCalculator     →  TaxBreakdown (fin-money's existing result type)
```

This split is deliberate: adding a second jurisdiction later means writing a new
`TaxDecisionResolver` + `TaxRateProvider` (and a new `TaxCalculator` implementation analogous to
`IndianTaxCalculator`), never touching this pipeline's shape or `fin-money`'s public API.

## Package layout

```
io.genfin.tax
├── api
│   ├── party        CountryCode, StateCode, RegistrationType, PartyTaxProfile
│   ├── supply        SupplyType (DOMESTIC/INTERSTATE/EXPORT/IMPORT/NONE)
│   ├── transaction   TransactionContext
│   ├── decision      TaxTreatment, TaxComponentType, TaxDecision
│   ├── rate          TaxRate
│   ├── metadata      TaxMetadataKeys — the fin-money TaxMetadata attribute keys this module reads
│   ├── config        IndianTaxConfiguration, ExportTreatment, RateConfiguration
│   └── exception     TaxErrorCode + exception hierarchy (built on fin-api's GenFinException)
├── port              TaxDecisionResolver, TaxRateProvider, DomesticPolicy, ExportPolicy,
│                     ImportPolicy, RegistrationPolicy, and their standard-factory classes
│                     (TaxDecisionResolvers, TaxRateProviders, TaxEngines)
├── internal          Default policy/resolver/rate-provider/calculator implementations
│                     (never exported by module-info)
└── spi               TaxExtensions — bootstraps defaults into a fin-api ExtensionRegistry
```

Depends on `fin-api` and `fin-money` (needed to implement `fin-money`'s existing `TaxCalculator`
port and reuse its `Money`/`Percentage`/`TaxContext`/`TaxBreakdown` types) — no framework
dependency, pure Java, same as every other Gen-Fin core module.

## Supported scenarios (V1)

| Rule | Seller | Buyer | Same state? | Treatment | Components |
|---|---|---|---|---|---|
| 1 | India | India | Yes | `CGST_SGST` | CGST + SGST |
| 2 | India | India | No | `IGST` | IGST |
| 3a | India (`LUT_REGISTERED`) | Foreign | — | `EXPORT_LUT` | EXPORT (0%) |
| 3b | India (`EXPORTER`, no LUT) | Foreign | — | `EXPORT_IGST` | IGST |
| 3c | India (other) | Foreign | — | configured default (`ExportTreatment`) | EXPORT or IGST |
| 4 | Foreign | India | — | `IMPORT_SERVICE` | NO_TAX (no calculation) |
| 5 | Foreign | Foreign | — | `NO_TAX` | NO_TAX |

Only `INDIA` is a supported jurisdiction. Every pairing that isn't Rule 1-4 above (i.e. neither
party is Indian) falls through to Rule 5 — `NO_TAX` — through the same configurable
`TaxDecisionResolver`, not a hardcoded catch-all.

## Explicitly unsupported (V2+)

GST return filing (GSTR-1, GSTR-3B), E-Way Bill, E-Invoicing/IRN, HSN/SAC lookup, Reverse Charge,
TDS, TCS, VAT, US Sales Tax, EU VAT, multi-country engines, tax persistence, Spring Boot
integration. Rule 4 (`IMPORT_SERVICE`) is deliberately a stub — no reverse-charge calculation
happens in V1; `ImportPolicy` exists specifically so that logic can be added later without changing
the resolver's shape.

## Extension points

Every pluggable piece is discoverable through the `fin-api` `ExtensionRegistry`
(`TaxExtensions.registerDefaults(registry)` wires in every built-in default — no
`switch`/`instanceof` anywhere in the dispatch path):

| Extension point | Purpose |
|---|---|
| `RegistrationPolicy` | Validates a party's registration standing beyond `PartyTaxProfile`'s own self-contained invariants |
| `DomesticPolicy` | Rule 1/2 — same-state vs. different-state |
| `ExportPolicy` | Rule 3 — LUT vs. IGST export treatment |
| `ImportPolicy` | Rule 4 — currently always `IMPORT_SERVICE`; the seam for future reverse-charge logic |
| `TaxDecisionResolver` | Composes the four policies above into one `TaxDecision` |
| `TaxRateProvider` | Supplies the configured `TaxRate` for each `TaxComponentType` — rates are never hardcoded |
| `TaxCalculator` (fin-money's own port) | `IndianTaxCalculator` / `DelegatingTaxCalculator` — the integration surface into `fin-money` |

## Integration with `fin-money` — and the registration-order requirement

`fin-money` already exposes a `TaxCalculator` extension point, defaulting to
`NoOpTaxCalculator` (always zero tax) via `MoneyExtensions.registerDefaults`. `fin-tax` provides
`DelegatingTaxCalculator` (wrapping `IndianTaxCalculator`, itself resolvable via
`TaxEngines.standard()`) as an additive implementation of that same port — `fin-money`'s public API
is unchanged.

Because `ExtensionRegistry.find()` returns the **first-registered** implementation for a type,
**call `TaxExtensions.registerDefaults(registry)` before `MoneyExtensions.registerDefaults(registry)`**
so the Tax Engine wins:

```java
ExtensionRegistry registry = ExtensionRegistries.create();
TaxExtensions.registerDefaults(registry);   // registers TaxCalculator first
MoneyExtensions.registerDefaults(registry); // its own NoOpTaxCalculator default now loses
```

Backward compatibility is preserved either way: a caller that never populates the seller/buyer
profile in `TaxContext.metadata()` (see below) gets exactly the same zero-tax result
`NoOpTaxCalculator` always gave — `DelegatingTaxCalculator` falls back internally rather than
failing, so registering the Tax Engine never breaks a caller that isn't using it yet.

### Passing seller/buyer profiles through the existing `TaxContext`

`fin-money`'s `TaxContext` was not changed — no new field was added to keep this integration purely
additive. Instead, `IndianTaxCalculator` reads the seller/buyer `PartyTaxProfile` out of
`TaxContext.metadata()` (a plain `Map<String, String>`) using the well-known keys in
`TaxMetadataKeys`:

```java
Map<String, String> attributes = new HashMap<>();
attributes.put(TaxMetadataKeys.SELLER_COUNTRY, "IN");
attributes.put(TaxMetadataKeys.SELLER_STATE, "MH");
attributes.put(TaxMetadataKeys.SELLER_REGISTRATION_TYPE, StandardRegistrationType.GST_REGISTERED.code());
attributes.put(TaxMetadataKeys.SELLER_GST_NUMBER, "27ABCDE1234F1Z5");
attributes.put(TaxMetadataKeys.BUYER_COUNTRY, "IN");
attributes.put(TaxMetadataKeys.BUYER_STATE, "KA");
attributes.put(TaxMetadataKeys.BUYER_REGISTRATION_TYPE, StandardRegistrationType.GST_REGISTERED.code());
attributes.put(TaxMetadataKeys.BUYER_GST_NUMBER, "29XYZAB5678C1Z3");

TaxContext context = new TaxContext(taxableAmount, new TaxMetadata(attributes), Instant.now());
Result<TaxComputationResult> result = registry.find(TaxCalculator.class).orElseThrow().calculate(context);
// result.get().breakdown() -> IGST 18% of taxableAmount (different states)
```

## Worked calculation example

₹1000 taxable amount, 18% combined rate (the `RateConfiguration.defaultIndianRates()` default:
CGST 9% + SGST 9%, or IGST 18%):

- Same state → `CGST 90.00` + `SGST 90.00` (total `180.00`)
- Different state → `IGST 180.00`
- Export under LUT → `EXPORT 0.00`
- Export under IGST → `IGST 180.00` (paid, refundable — V1 doesn't model the refund claim)
- Foreign↔India or foreign↔foreign → `0.00`

## Validation

- `PartyTaxProfile`'s own constructor rejects a `GST_REGISTERED` party with no GST number, and an
  `LUT_REGISTERED` party with no LUT number.
- `RegistrationPolicy` (invoked by the resolver before every decision) rejects a `GST_REGISTERED`
  Indian party with no state on file — the domestic same-state/different-state decision depends on
  it.
- `IndianTaxCalculator` returns a `Result.failure(TaxErrorCode.MISSING_TAX_PROFILE, ...)` — not an
  exception — when a required seller or buyer profile is absent from `TaxContext.metadata()`,
  since that's `TaxCalculator`'s own `Result`-returning contract.
- `IndianTaxCalculator` returns `Result.failure(TaxErrorCode.UNSUPPORTED_TRANSACTION, ...)` when the
  resolved treatment needs a `TaxRate` no `TaxRateProvider` has configured.
- An unrecognised `RegistrationType` code doesn't break resolution — it's simply treated as neither
  `LUT_REGISTERED` nor `EXPORTER` by the export policy's string-equality checks, matching the
  open-value-type convention used throughout Gen-Fin.

## Future roadmap

Additional jurisdictions (UAE VAT, EU VAT OSS, US Sales Tax nexus rules) each become a new
`TaxDecisionResolver` + `TaxRateProvider` + `TaxCalculator` triple, following exactly the pattern
`IndianTaxCalculator` establishes — no change to `fin-money`'s `TaxCalculator` port. Reverse-charge
handling for Rule 4 (`IMPORT_SERVICE`) plugs into `ImportPolicy`. GST compliance concerns (return
filing, E-Invoicing/IRN, HSN/SAC catalogs, TDS/TCS) are a deliberately separate, much larger body of
work — Tax Engine V2+, not this module's job.

## See also

[ARCHITECTURE.md](ARCHITECTURE.md), [EXTENSIONS.md](EXTENSIONS.md), [MONEY_ENGINE.md](MONEY_ENGINE.md)
for the `TaxCalculator` port this integrates with, [MODULES.md](MODULES.md), [SPRING.md](SPRING.md).
