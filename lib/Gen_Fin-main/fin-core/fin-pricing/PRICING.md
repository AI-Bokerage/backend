# fin-pricing

`fin-pricing` is internally a **Commercial Engine** — a Product Catalog, Pricing Engine, Discount
Engine, Coupon Engine, Promotion Engine, Credit Engine, Quote Engine, and Commercial Rule Engine,
all in one cohesive module — even though the module keeps the name `fin-pricing` for simplicity. It
calculates the commercial **value** of a request before any invoice exists. It **never creates
invoices, never captures payments, never posts accounting entries, and never calculates
jurisdiction-specific tax** (GST/VAT/IGST/CGST/...) — only a `TaxPlaceholder` extension point for a
future Tax Engine to plug into. It produces immutable `PricingResult`s that later modules (Invoice,
Payment, Ledger) will consume — but `fin-pricing` itself has **zero dependency** on any of them.

This document covers the architecture, the pricing flow / pipeline, the catalog, the Discount
Engine, the Coupon Engine, the Promotion Engine, the Credit Engine, the Quote Engine, the
Commercial Rule Engine, extension points, and a short usage example.

## Architecture

`fin-pricing` depends on nothing but `fin-api` and `fin-money`. It is deliberately blind to
Invoice/Payment/Refund/Reconciliation/Ledger and to every provider — an `ArchUnit`-style
architecture test (`PricingArchitectureTest`) fails the build if a single import ever crosses that
line. Products, coupons, credit wallets, and commercial rules are all **application-owned** —
`fin-pricing` defines the contracts (`CatalogItem`/`ProductReference`/`ServiceReference`,
`Coupon`/`CouponCode`/`CouponValidator`, `CreditWallet`, `CommercialRule`) and the SPI registries
they live in, but ships none of the actual data. Every configurable concern
(catalog resolution, discounting, promoting, coupon redemption, credit drawdown, commercial rule
enforcement) is a Strategy/Policy/Registry resolved through `ExtensionRegistry` — the pricing
pipeline itself never hardcodes a percentage, a fixed amount, or a business rule.

### Package layout

Same convention as `fin-ledger`/`fin-refund`/`fin-reconciliation`:

- `io.genfin.pricing.<concept>` — public model/value types: `id`, `pricing` (core aggregate:
  `PricingRequest`/`PricingResult`/`PricingContext`), `catalog`, `price` (`Price`/
  `PriceComponent`/`PriceBreakdown`), `calculation`, `pipeline`, `discount`, `promotion`, `coupon`,
  `credit`, `quote`, `rule` (Commercial Rule Engine), `strategy` (Pricing Strategies), `tax`
  (`TaxPlaceholder`), `lifecycle`, `validation`, `config`, `event`, `spi`, `serialization`.
- `io.genfin.pricing.port.<concern>` — SPI interfaces per concern.
- `io.genfin.pricing.internal.<concern>` — default implementations, never exported from
  `module-info.java`.
- `io.genfin.pricing.spi.PricingExtensions` — registers every default extension into an
  `ExtensionRegistry` in one call.

## The PricingRequest aggregate

`PricingRequest` carries a list of `Line`s (`CatalogId` + quantity), an SPI-driven lifecycle
(`CREATED → VALIDATING → CALCULATING → PRICED`, with `REJECTED`/`EXPIRED` as terminal failure
states), and free-form `PricingAttributes`/`PricingMetadata`. It records domain events
(`PricingStarted`, `PricingCompleted`, `PricingRejected`, ...) as it transitions. `PricingResult` is
the immutable output: a `PricingSummary` (base/reduction/net amounts), `PricingMetadata`, and a
`PricingVersion` that bumps on every repricing of the same request — a `PricingResult` is never
mutated, only ever superseded.

`PricingRequestBuilder` builds a request fluently; `PricingRequestFactory` does the same while
resolving the lifecycle provider from an `ExtensionRegistry`.

## Pricing flow / pipeline

The calculation engine is a sequence of independently replaceable stages, not one monolithic
calculator. `PricingPipeline` (`port.pipeline`, default `DefaultPricingPipeline`) runs an ordered
list of `PricingPipelineStage`s, threading a running `CalculationResult` from one stage to the
next, starting from `CalculationResult.empty()`:

```
PricingRequest
     |
     v
Catalog Resolution   -- looks up each Line's CatalogItem via CatalogRegistry
     |
     v
Base Price Resolution -- PricingPolicy resolves a Price per Line via PricingStrategy
     |
     v
Discount Engine       -- DiscountPolicy applies registered Discount/DiscountRule strategies
     |
     v
Promotion Engine      -- PromotionPolicy applies registered PromotionStrategy campaigns
     |
     v
Coupon Engine         -- CouponPolicy redeems a submitted code via CouponStrategy/CouponRegistry
     |
     v
Credit Engine         -- CreditPolicy draws down a CreditWallet via CreditStrategy/CreditCalculator
     |
     v
Tax Placeholder       -- TaxPlaceholder extension point (no-op by default; never computes GST/VAT)
     |
     v
Rounding & Money Policy -- pass-through stage, ready for a rounding policy to plug into
     |
     v
Pricing Validation    -- PricingValidator runs every ValidationRule, collects all issues
     |
     v
PricingResult
```

`PricingPipelines.standard(...)` wires this exact stage order; `PricingPipelines.of(stages)` builds
one from any custom stage list. Every stage is independently resolvable/replaceable via
`ExtensionRegistry` (see `PricingPipelines.from`). `PricingEngine` (default in `calculation`)
collapses a completed pipeline run into a versioned `PricingResult`.

## Catalog

`Catalog`/`CatalogItem`/`CatalogGroup`/`CatalogCategory` (`io.genfin.pricing.catalog`) are generic
abstractions a consuming application populates — `fin-pricing` hardcodes no actual products.
`CatalogItem` wraps either a `ProductReference` or a `ServiceReference` plus `CatalogAttributes`.
`CatalogRegistry` (`port.catalog`) is the SPI a `CatalogItemProvider` backs; `CatalogRegistries
.empty()` is the intentional default until an application registers its own.

## Discount Engine

`Discount` (`io.genfin.pricing.discount`) is a sealed-by-convention set of shapes —
`PercentageDiscount`, `FixedDiscount`, `TierDiscount`, `VolumeDiscount`, `ConditionalDiscount` — all
implementing one `applyTo(runningAmount, line, context)` contract, so the Discount Engine never
branches on which kind of discount it is holding. `DiscountRule` pairs a `Discount` with a
`DiscountCondition` predicate; `DiscountStrategies.fromRules(rules)` builds a `DiscountStrategy`
from a flat rule list. `DiscountCalculator` stacks a list of `Discount`s against a `Price`'s running
amount, appending one `PriceComponent` per discount so the full stack stays visible in the
`PriceBreakdown`. `DiscountValidator` (`DiscountValidators.standard()`) flags a negative net amount.

## Promotion Engine

`Promotion`/`PromotionCampaign`/`PromotionRule`/`PromotionEligibility` mirror the Discount Engine's
shape at campaign granularity, with `PromotionPriority` and a `PricingConflictStrategy` (see
`io.genfin.pricing.strategy`: `FirstApplicableStrategy`, `HighestDiscountStrategy`,
`MaximumSavingsStrategy`, `BestPriceStrategy`, `PriorityOrderStrategy`, `ExclusivePromotionStrategy`)
resolving which campaign wins when more than one is eligible. `PromotionPolicy`
(`PromotionPolicies.of(strategies, conflictStrategy)`) is the seam the Promotion Engine stage calls.

## Coupon Engine

Coupons are **application-provided** — `fin-pricing` defines `Coupon`/`CouponCode`/
`CouponCampaign`/`CouponValidator` and a `CouponRegistry` SPI (tracking `CouponUsage`/
`CouponRedemption`), but never ships or stores actual coupon data. `CouponStrategy` resolves a
candidate code from the `PricingContext` (the default, `CouponStrategies.submitted()`, reads a
submitted code attribute); `CouponPolicy` validates it against the registry
(`CouponValidators.standard(registry)`) before redeeming. A redeemed coupon can only ever reduce a
price — `CouponValidationRule` rejects any coupon component that would raise it.

## Credit Engine

`CreditWallet`/`Credit`/`CreditBalance`/`CreditAllocation`/`CreditUsage` model a store-credit-style
balance keyed by `WalletId` and `CreditCategory`. `CreditStrategy` resolves a candidate wallet
(default `CreditStrategies.fromAttribute()`, reading a wallet reference off the request's
attributes); `CreditCalculator` (default `CreditCalculators.fullBalance()`) decides how much of the
resolved wallet to draw down; `CreditRegistry`/`CreditValidator` enforce that a wallet is registered
and has sufficient balance before it is applied. Like coupons, credit can only ever reduce a price.

## Quote Engine

`Quote`/`QuoteItem`/`QuoteSummary`/`QuoteVersion` (`io.genfin.pricing.quote`) snapshot a
`PricingResult` into a shareable, expirable commercial offer — `QuoteExpiration` and `QuoteStatus`
track its own short lifecycle independent of the `PricingRequest` that produced it. `QuoteBuilder`/
`QuoteFactory` construct one; `QuotePolicy` (`QuotePolicies.standard()`) decides default expiration
and versioning behavior.

## Commercial Rule Engine

`CommercialRule` (`port.rule`) is the SPI a business threshold plugs into — shipped examples
(`internal.rule`) include `MinimumPriceRule`, `MaximumDiscountRule`, `CreditLimitRule`,
`CouponStackingLimitRule`, `PromotionStackingLimitRule`, `RegionalPricingRule`, and
`PartnerPricingRule`, none of them wired in by default. `CommercialRuleRegistry`
(`CommercialRuleRegistries.empty()`) is the application-populated catalog; `CommercialRuleEngine`
runs every registered rule against a `RuleContext`, producing `RuleResult`s that
`RuleValidationRule` folds into the same `ValidationResult` as every other Pricing Validation
finding — a commercial-rule violation surfaces exactly like a structural one.

## Extension points (SPI)

Every pluggable concern is registered through an `ExtensionRegistry`:

| Concern | Port | Default |
|---|---|---|
| Lifecycle | `port.lifecycle.PricingLifecycleProvider` | `PricingLifecycles.standard()` |
| Catalog | `port.catalog.CatalogRegistry` | `CatalogRegistries.empty()` (application-registered) |
| Base price | `port.calculation.PricingPolicy` / `PricingStrategy` | `PricingPolicies.empty()` (application-registered) |
| Pricing conflict resolution | `port.strategy.PricingConflictStrategy` | `PricingStrategies.priorityOrder()` |
| Discount | `port.discount.DiscountPolicy` / `DiscountStrategy` / `DiscountValidator` | `DiscountPolicies.empty()` / `DiscountValidators.standard()` |
| Promotion | `port.promotion.PromotionPolicy` / `PromotionStrategy` | `PromotionPolicies.empty()` |
| Coupon | `port.coupon.CouponRegistry` / `CouponPolicy` / `CouponStrategy` / `CouponValidator` | `CouponRegistries.empty()` / `CouponPolicies.empty()` / `CouponStrategies.submitted()` / `CouponValidators.standard(registry)` |
| Credit | `port.credit.CreditRegistry` / `CreditPolicy` / `CreditStrategy` / `CreditValidator` | `CreditRegistries.empty()` / `CreditPolicies.empty()` / `CreditStrategies.fromAttribute()` / `CreditValidators.standard(registry)` |
| Quote | `port.quote.QuotePolicy` | `QuotePolicies.standard()` |
| Commercial rules | `port.rule.CommercialRuleRegistry` / `CommercialRuleEngine` | `CommercialRuleRegistries.empty()` / `CommercialRuleEngines.of(registry)` |
| Tax placeholder | `io.genfin.pricing.tax.TaxPlaceholder` | `TaxExtensionPoint.noOp()` |
| Pricing validation | `port.validation.PricingValidator` / `ValidationRule` | `Validators.standard()` / `Validators.defaultRules()` |
| Pipeline | `port.pipeline.PricingPipeline` | `PricingPipelines.from(registry, ...)` |
| Pricing engine | `port.calculation.PricingEngine` | `PricingEngines.of(pipeline)` |

`PricingExtensions.registerDefaults(registry)` registers all of the above in one call. Catalog
items, coupons, credit wallets, and commercial rules all register **empty** by design — a consuming
application populates them before pricing anything real.

## Configuration

`PricingConfiguration` (`io.genfin.pricing.config`) is the explicit, fully validated policy set for
a deployment, composed of six sub-configurations (`CatalogConfiguration`, `DiscountConfiguration`,
`PromotionConfiguration`, `CouponConfiguration`, `CreditConfiguration`, `QuoteConfiguration`,
`CommercialRuleConfiguration`) alongside the cross-cutting pricing/lifecycle/tax/validation
policies — every field is validated non-null at build time. `PricingConfigurations.standard()`
returns the out-of-the-box configuration built from empty/default sub-configurations.

## Usage example

```java
Currency usd = CurrencyFactory.newCurrency().code("USD").symbol("$")
    .displayName("US Dollar").build();

CatalogId proId = CatalogId.generate();
CatalogItem proPlan = new CatalogItem(proId, new ProductReference("plan-pro"));
CatalogRegistry catalogRegistry = CatalogRegistries.withProvider(() -> List.of(proPlan));

// Application-registered base price strategy.
PricingStrategy flatUnitPrice = new PricingStrategy() {
  public boolean supports(PricingRequest.Line line, PricingContext context) {
    return line.catalogId().equals(proId);
  }
  public Price resolve(PricingRequest.Line line, PricingContext context) {
    Money unit = Money.of(20, usd).multiply(BigDecimal.valueOf(line.quantity()));
    return new Price(line.catalogId(),
        PriceBreakdown.of(PriceComponent.of(PriceType.BASE, "base", unit)));
  }
};

// Application-registered discount: 10% off, unconditionally.
DiscountRule tenPercentOff = DiscountRule.of("STANDARD_DISCOUNT",
    PercentageDiscount.of("10% off", Percentage.ofPercent(BigDecimal.TEN)),
    (line, context) -> true);

PricingPipeline pipeline = PricingPipelines.standard(
    PricingPolicies.of(List.of(flatUnitPrice)),
    DiscountPolicies.of(List.of(DiscountStrategies.fromRules(List.of(tenPercentOff)))),
    PromotionPolicies.empty(),
    CouponPolicies.empty(), CouponRegistries.empty(),
    CreditPolicies.empty(), CreditRegistries.empty(),
    catalogRegistry, List.of());
PricingEngine engine = PricingEngines.of(pipeline);

PricingRequest request = PricingRequestBuilder.newRequest()
    .line(new PricingRequest.Line(proId, 3))
    .requestedAt(Instant.now())
    .build();

PricingResult result = engine.calculate(request, PricingVersion.initial(), Instant.now());

// baseAmount = 60, reductionAmount = -6, netAmount = 54
assert result.summary().netAmount().equals(Money.of(54, usd));
```

## Future integration path — without touching Invoice/Payment/Reconciliation/Ledger today

`fin-pricing` produces `PricingResult` and stops. The intended downstream flow is:

```
PricingResult -> Invoice Engine -> Payment -> Reconciliation -> Ledger
```

An Invoice Engine (not part of this module) will read a `PricingResult`'s `PricingSummary` through
`fin-pricing`'s public API and turn it into billable invoice lines; from there Payment,
Reconciliation, and Ledger pick up exactly as `fin-ledger`'s own `FinancialFact` normalization
already documents. **None of that exists as a dependency of `fin-pricing` today** — the
architecture test enforces that `fin-pricing` never imports `io.genfin.invoice`, `io.genfin.payment`,
`io.genfin.refund`, `io.genfin.reconciliation`, `io.genfin.ledger`, or any provider package. Every
future integration reads `fin-pricing`'s published `pricing`/`price`/`id` types from the outside;
`fin-pricing` never reaches back.
