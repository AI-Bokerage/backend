# Pricing Engine (`fin-pricing`)

A reusable Pricing Engine: catalog, discount/promotion/coupon/credit engines, quote engine, and a
commercial rule engine, orchestrated by a `PricingPipeline`. `PricingRequest` is fully decoupled
from Invoice/Payment/Ledger — it never holds an invoice, a payment, or a ledger entry. Depends
only on `fin-api` and `fin-money`.

**Full documentation:** [`fin-core/fin-pricing/PRICING.md`](fin-core/fin-pricing/PRICING.md) —
architecture, the `PricingRequest` aggregate, the pricing pipeline, catalog/discount/promotion/
coupon/credit/quote/commercial-rule engines, extension points, configuration, and a usage example.

## At a glance

| Concern | Key types |
|---|---|
| Input | `PricingRequest` (lines, no upstream engine coupling) |
| Calculation | `PricingEngine` — the one real top-level facade (see [SPRING.md](SPRING.md)) |
| Catalog | `CatalogRegistry` (ships **empty** — you register your own products/prices) |
| Discount/Promotion/Coupon/Credit | All ship as empty policies — no built-in business rules |
| SPI registration | `io.genfin.pricing.spi.PricingExtensions.registerDefaults(registry)` |

## Integration note

`CatalogRegistry`/`PricingPolicy`/`DiscountPolicy`/etc. are empty by design, matching every other
Gen-Fin engine's "we hardcode no business rules" philosophy — a FIN-SVC deployment supplies its
own catalog and pricing strategies. See [INTEGRATION.md](INTEGRATION.md). Rounding is fin-money's
concern (`RoundingStrategies`), not fin-pricing's — there is no `RoundingMode` property here.

## See also

[ARCHITECTURE.md](ARCHITECTURE.md), [EXTENSIONS.md](EXTENSIONS.md), [INTEGRATION.md](INTEGRATION.md),
[SPRING.md](SPRING.md).
