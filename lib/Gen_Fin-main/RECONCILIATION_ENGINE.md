# Reconciliation Engine (`fin-reconciliation`)

A reusable Reconciliation Engine: matching strategies/policies, comparison strategies/policies,
discrepancy detection, tolerance calculation, a rule engine, and summary/variance reporting.
Depends on `fin-api`, `fin-money`, `fin-payment`, and `fin-refund` (reuses `fin-refund`'s
`Reference` type rather than inventing its own — see [INTEGRATION.md](INTEGRATION.md)).

**Full documentation:**
[`fin-core/fin-reconciliation/RECONCILIATION.md`](fin-core/fin-reconciliation/RECONCILIATION.md)
— package layout, the Reconciliation aggregate, lifecycle, matching flow, comparison/discrepancy/
tolerance, rule engine, summary/reporting, extension points, configuration, and a usage example.

## At a glance

| Concern | Key types |
|---|---|
| Input | `ReconciliationItem.of(Reference, Money)` — `Reference` is `fin-refund`'s type, reused |
| Matching | `MatchingEngine`, `MatchingStrategy` (chained), `MatchingPolicy` |
| Comparison | `ComparisonStrategy` (chained), `ComparisonPolicy` |
| Discrepancy | `DiscrepancyDetector`, `DiscrepancyPolicy`, `DiscrepancyReasonProvider` |
| Rules | `ReconciliationRule` (ships defaults), `RuleEngine` |
| SPI registration | `io.genfin.reconciliation.spi.ReconciliationExtensions.registerDefaults(registry)` |

## Integration note

Because `ReconciliationItem` uses the same `Reference` type `fin-refund` and `fin-ledger` also
consume, a FIN-SVC deployment can produce reconciliation input directly from Payment/Refund public
output with zero extra glue code — see [INTEGRATION.md](INTEGRATION.md).

## See also

[ARCHITECTURE.md](ARCHITECTURE.md), [EXTENSIONS.md](EXTENSIONS.md), [INTEGRATION.md](INTEGRATION.md),
[SPRING.md](SPRING.md).
