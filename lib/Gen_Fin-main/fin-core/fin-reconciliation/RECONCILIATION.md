# fin-reconciliation

`fin-reconciliation` is a reusable Java domain module that compares and links financial records
originating from different systems — internal payment vs. provider settlement vs. refund vs. bank
record. It **never performs payments or accounting**; it only compares, links, classifies, and
reports on financial consistency. It is its own standalone module, not a sub-feature of a future
ledger — reconciliation is needed even in systems with no accounting ledger at all (e-commerce,
SaaS billing, marketplaces, donation platforms).

This document covers the architecture, the lifecycle, the matching flow, the rule engine,
extension points, configuration, and a short usage example.

## Package layout

Same convention as `fin-refund`/`fin-payment`:

- `io.genfin.reconciliation.<concept>` — public model/value types (`id`, `reconciliation`,
  `lifecycle`, `matching`, `comparison`, `discrepancy`, `tolerance`, `rule`, `summary`, `report`,
  `config`, `calculation`, `validation`, `event`, `serialization`, `spi`).
- `io.genfin.reconciliation.port.<concern>` — SPI interfaces per concern (`port.lifecycle`,
  `port.matching`, `port.comparison`, `port.discrepancy`, `port.tolerance`, `port.rule`,
  `port.calculation`, `port.validation`, `port.report`).
- `io.genfin.reconciliation.internal.<concern>` — default implementations, never exported from
  `module-info.java`.
- `io.genfin.reconciliation.spi.ReconciliationExtensions` — registers every default extension into
  an `ExtensionRegistry` in one call.

## The Reconciliation aggregate

`Reconciliation` extends `AggregateRoot<ReconciliationId>`. It is constructed with a
`ReconciliationBatchId` and holds a list of `ReconciliationItem`s — each one a generic `Reference`
(`io.genfin.refund.reference.Reference`, reused as-is because `fin-refund` already exports it
publicly) paired with a `Money` amount. `Reconciliation` never owns a `Payment`, `Invoice`,
`Refund`, settlement, or bank-transaction object directly — only opaque references to them, the
same way `Refund` only ever holds a `Reference` to the payment it refunds.

`ReconciliationBuilder`/`ReconciliationItemBuilder` build the aggregate and its items fluently;
`ReconciliationFactory.create(registry, batchId)` builds one wired to whatever
`ReconciliationLifecycleProvider` is registered in an `ExtensionRegistry`, falling back to
`ReconciliationLifecycles.standard()`.

## Lifecycle

```
CREATED --COLLECT--> COLLECTING --MATCH--> MATCHING --ANALYZE--> ANALYZING
CREATED --CANCEL--> CANCELLED
COLLECTING --CANCEL--> CANCELLED
MATCHING --CANCEL--> CANCELLED
ANALYZING --RECONCILE--> RECONCILED
ANALYZING --PARTIALLY_RECONCILE--> PARTIALLY_RECONCILED
ANALYZING --FAIL--> FAILED
ANALYZING --CANCEL--> CANCELLED
PARTIALLY_RECONCILED --RECONCILE--> RECONCILED
PARTIALLY_RECONCILED --FAIL--> FAILED
FAILED --COLLECT--> COLLECTING          (retry)
```

The transition table lives in `internal.lifecycle.DefaultReconciliationLifecycleProvider`, built
on `io.genfin.api.statemachine.StateMachine`. `Reconciliation` holds no transition logic of its
own — it calls `lifecycle.fire(event)` and throws `IllegalStateException` when a transition isn't
allowed. `reconcile(...)` refuses to run while any discrepancy is still open;
`reconcilePartially(...)` requires at least one. Each phase transition (`startCollecting`,
`startMatching`/`startAnalysis`, `reconcile`/`reconcilePartially`, `fail`) records a matching
domain event (`ReconciliationStarted`, `MatchingCompleted`, `ComparisonCompleted`,
`ReconciliationCompleted`, `ReconciliationFailed`, `DiscrepancyDetected`), retrievable with
`pullEvents()`.

Swap the whole lifecycle by implementing `port.lifecycle.ReconciliationLifecycleProvider` and
registering it in place of `ReconciliationLifecycles.standard()` — no code in `Reconciliation`
itself changes.

## Matching flow

A reconciliation run pairs up items from two sources (e.g. internal payments vs. provider
settlements). `MatchingEngine.match(left, right, context)` greedily assigns each left item to its
best available right candidate using a `MatchingStrategy`, producing one `MatchResult` per left
item (`MATCHED`, `PARTIALLY_MATCHED`, or `NOT_MATCHED`, with an optional counterpart id, confidence
score, and variance amount).

Default `MatchingStrategy` implementations, all in `matching.MatchingStrategies`:

| Strategy | Agrees when |
|---|---|
| `exact()` | Amount and reference are both identical. |
| `amount()` | The `Money` amount is identical, ignoring reference. |
| `reference()` | The `Reference` is identical, ignoring amount. |
| `tolerance()` | The amount gap is within the `MatchingContext`'s configured tolerance. |
| `partial()` | The right side is less than the left (captures the shortfall as variance). |
| `fuzzy()` | References agree once punctuation/case differences are normalized away. |
| `date()` | Both candidates carry a timestamp within the context's tolerance window. |
| `allOf(strategies)` / `anyOf(strategies)` | Every / any of the composed strategies agrees (`CompositeMatch`). |

`MatchingStrategies.standardChain()` is the default fallback order: exact, then tolerance, then
reference, then fuzzy, then partial. `MatchingEngines.standard()` runs that chain (or whichever
`MatchingPolicy` is configured) against every left/right pair.

## Comparison, discrepancy, and tolerance

Once matched, `ComparisonStrategy` implementations (`comparison.ComparisonStrategies`) compare
attributes field-by-field — amount, currency, status, timestamp, reference, metadata — each
producing zero or more `Difference`s. `DiscrepancyDetector` turns differences that a
`DiscrepancyPolicy` considers significant into `Discrepancy` records, categorized by
`DiscrepancyCategory`/`StandardDiscrepancyReason` and rated by `DiscrepancySeverity`.

`ToleranceCalculator` (`port.tolerance.ToleranceCalculator`, default
`internal.tolerance.DefaultToleranceCalculator`) decides whether a given `Tolerance` (`Amount`,
`Percentage`, `Currency`, `Date`, or a registered `Custom` rule via `CustomToleranceRuleRegistry`)
accepts a given variance, returning a `ToleranceResult`/`ToleranceOutcome` rather than a bare
boolean so the reason is always available for reporting.

## Rule engine

`RuleEngine.evaluate(left, right, context)` (`internal.rule.DefaultRuleEngine`) runs every
registered `ReconciliationRule` and **collects all results** — it never short-circuits on the
first rule that reports a problem, mirroring `fin-refund`'s `DefaultRefundValidator`. Default
rules, all in `rule.ReconciliationRules.defaultRules()`:

| Rule | Rule code | Fires when |
|---|---|---|
| `AmountsEqualRule` | `AMOUNTS_EQUAL` | The two records' amounts differ. |
| `AmountsWithinToleranceRule` | `AMOUNTS_WITHIN_TOLERANCE` | The amount gap exceeds the context's configured tolerance. |
| `CurrenciesEqualRule` | `CURRENCIES_EQUAL` | The two records' currencies differ (suppresses `AMOUNTS_EQUAL` since the amounts aren't comparable). |
| `ReferencesEqualRule` | `REFERENCES_EQUAL` | The two records' references differ. |
| `RefundExistsRule` | `REFUND_EXISTS` | `RuleContext` flags a refund exists against this item that isn't otherwise accounted for. |
| `DuplicatePaymentRule` | `DUPLICATE_PAYMENT` | `RuleContext` flags the payment side as a duplicate. |
| `DuplicateRefundRule` | `DUPLICATE_REFUND` | `RuleContext` flags the refund side as a duplicate. |
| `MissingSettlementRule` | `MISSING_SETTLEMENT` | `RuleContext` flags no settlement record was found. |
| `MissingPaymentRule` | `MISSING_PAYMENT` | `RuleContext` flags no payment record was found. |
| `ExpiredTransactionRule` | `EXPIRED_TRANSACTION` | `RuleContext` flags the transaction as expired. |

Each rule reads its own concern only — the `RuleEngine` doesn't know or care which rules are
registered, so adding a rule is purely additive: implement `ReconciliationRule` and register it,
no `switch`/`instanceof` dispatch anywhere in the engine.

## Summary and reporting

`SummaryCalculator` (`internal.calculation.DefaultSummaryCalculator`) rolls a batch of
`MatchResult`s and `Discrepancy`s up into a `ReconciliationSummary` — counts by match outcome,
outstanding difference totals per `Currency` (a run can span several), and discrepancy counts by
severity. `ReconciliationSummary.isFullyReconciled()` is true only when nothing is unmatched,
nothing is partially matched, and no discrepancy remains open.

`ReportFormatter` (default `internal.report.PlainTextReportFormatter`) renders a
`ReconciliationReport` (built from `ReportSection`/`ReportEntry`) as plain text; swap it for a
different `ReportFormatter` to change output format without touching how the report is built.
`serialization.ReportEntryCodec` gives a dependency-free (no Jackson/Gson) text encoding for a
single `ReportEntry`.

## Extension points (SPI)

Every pluggable concern is registered through an `ExtensionRegistry`:

| Concern | Port | Default |
|---|---|---|
| Lifecycle | `port.lifecycle.ReconciliationLifecycleProvider` | `ReconciliationLifecycles.standard()` |
| Matching strategy | `port.matching.MatchingStrategy` | `MatchingStrategies.standardChain()` |
| Matching policy | `port.matching.MatchingPolicy` | `MatchingPolicies.standard()` |
| Matching engine | `port.matching.MatchingEngine` | `MatchingEngines.standard()` |
| Comparison strategy | `port.comparison.ComparisonStrategy` | `ComparisonStrategies.standardChain()` |
| Comparison policy | `port.comparison.ComparisonPolicy` | `ComparisonPolicies.standard()` |
| Discrepancy reasons | `port.discrepancy.DiscrepancyReasonProvider` | `DiscrepancyReasonRegistries.standardCatalog()` |
| Discrepancy policy | `port.discrepancy.DiscrepancyPolicy` | `DiscrepancyPolicies.standard()` |
| Discrepancy detection | `port.discrepancy.DiscrepancyDetector` | `DiscrepancyDetectors.standard()` |
| Tolerance calculation | `port.tolerance.ToleranceCalculator` | `ToleranceCalculators.standard()` |
| Reconciliation rules | `port.rule.ReconciliationRule` | `ReconciliationRules.defaultRules()` |
| Rule engine | `port.rule.RuleEngine` | `RuleEngines.standard()` |
| Summary calculation | `port.calculation.SummaryCalculator` | `SummaryCalculators.standard()` |
| Difference calculation | `port.calculation.DifferenceCalculator` | `DifferenceCalculators.standard()` |
| Variance calculation | `port.calculation.VarianceCalculator` | `VarianceCalculators.standard()` |
| Report formatting | `port.report.ReportFormatter` | `ReportFormatters.standard()` |
| Validation | `port.validation.ReconciliationValidator` | `Validators.standard()` |

`ReconciliationExtensions.registerDefaults(registry)` registers all of the above in one call.
Applications override any single one by registering their own implementation of the port
interface **after** calling `registerDefaults`, or by building a custom `ReconciliationConfiguration`
(see below) and never calling `registerDefaults` at all.

## Configuration

`ReconciliationConfiguration` (in `io.genfin.reconciliation.config`) is the explicit, fully
populated policy set for a deployment, composed of four validated sub-configurations
(`ToleranceConfiguration`, `MatchingConfiguration`, `RuleConfiguration`,
`ReportingConfiguration`) alongside the lifecycle/comparison/discrepancy policies — every field is
validated non-null at build time, so a misconfigured deployment fails fast:

```java
ReconciliationConfiguration configuration = ReconciliationConfiguration.builder()
    .lifecycleProvider(ReconciliationLifecycles.standard())
    .comparisonPolicy(ComparisonPolicies.standard())
    .discrepancyDetector(DiscrepancyDetectors.standard())
    .discrepancyPolicy(DiscrepancyPolicies.standard())
    .toleranceConfiguration(ToleranceConfiguration.builder()
        .calculator(ToleranceCalculators.standard())
        .customRules(ToleranceCalculators.newCustomRuleRegistry())
        .build())
    .matchingConfiguration(MatchingConfiguration.builder()
        .engine(MatchingEngines.standard())
        .policy(MatchingPolicies.standard())
        .strategies(MatchingStrategies.standardChain())
        .build())
    .ruleConfiguration(RuleConfiguration.builder()
        .engine(RuleEngines.standard())
        .rules(ReconciliationRules.defaultRules())
        .build())
    .reportingConfiguration(ReportingConfiguration.builder()
        .summaryCalculator(SummaryCalculators.standard())
        .formatter(ReportFormatters.standard())
        .build())
    .build();
```

`ReconciliationConfigurations.standard()` returns the boring, out-of-the-box configuration built
from exactly the defaults above.

## Usage example

```java
Currency usd = CurrencyFactory.newCurrency().code("USD").symbol("$")
    .displayName("US Dollar").fractionDigits(2).build();

// One item from the payment engine, one from the provider settlement feed.
ReconciliationItem paymentSide = ReconciliationItem.of(
    Reference.payment("PAY-1"), Money.of(new BigDecimal("100.00"), usd));
ReconciliationItem settlementSide = ReconciliationItem.of(
    Reference.of(StandardReferenceType.PAYMENT, "PAY-1"), Money.of(new BigDecimal("99.50"), usd));

ReconciliationConfiguration config = ReconciliationConfigurations.standard();

Reconciliation reconciliation = ReconciliationBuilder.newReconciliation()
    .batchId(ReconciliationBatchId.generate())
    .item(paymentSide)
    .item(settlementSide)
    .build();

reconciliation.startCollecting(ClockProviders.system());
reconciliation.startMatching();

List<MatchResult> results = config.matchingConfiguration().engine().match(
    List.of(MatchCandidate.of(paymentSide)),
    List.of(MatchCandidate.of(settlementSide)),
    MatchingContext.of(Money.of(new BigDecimal("1.00"), usd)));

reconciliation.startAnalysis(ClockProviders.system());
if (results.stream().anyMatch(r -> r.outcome() != MatchOutcome.MATCHED)) {
  reconciliation.recordDiscrepancy("settlement short by 0.50", ClockProviders.system());
  reconciliation.reconcilePartially(ClockProviders.system());
} else {
  reconciliation.reconcile(ClockProviders.system());
}
```

## How applications integrate — without touching payment/refund/money

1. Depend on `fin-reconciliation` (which transitively brings `fin-api`, `fin-money`,
   `fin-payment`, `fin-refund`).
2. Build a `ReconciliationItem` per side being compared, using `Reference` to point at whatever
   payment, settlement, refund, or bank record it represents — `fin-reconciliation` never touches
   those engines' internal packages, only their published `Reference`/`Money`/id types.
3. Run `MatchingEngine`/`ComparisonPolicy`/`RuleEngine` from a `ReconciliationConfiguration`
   (custom, or `ReconciliationConfigurations.standard()`) to link and classify the items.
4. Drive the `Reconciliation` aggregate through its lifecycle as matching/analysis progresses, and
   persist it plus its `pullEvents()` output using your own application's persistence and
   messaging — `fin-reconciliation` has no persistence, HTTP, or messaging concept of its own by
   design.

Because every extension point is registered through an `ExtensionRegistry` and the only
touchpoints into `fin-payment`/`fin-refund`/`fin-money` are their existing public
`Reference`/`Money`/id types, applications built on `fin-reconciliation` never need to modify
those modules to add, change, or replace reconciliation behavior.
