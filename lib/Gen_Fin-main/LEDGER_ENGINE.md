# Ledger Engine (`fin-ledger`)

A reusable Java Accounting Engine: Chart of Accounts, Journal Engine, Double-Entry Posting Engine,
Balance Engine, Accounting Period Engine, Trial Balance, Financial Statement models, Reversal
Engine, and Adjustment Engine. Never performs payments, refunds, or reconciliation — it consumes
already-verified financial facts (from `fin-reconciliation`, or directly from
`fin-payment`/`fin-refund` once an application considers them settled) and turns them into
balanced journal entries. Hardcodes no business account names or posting rules.

**Full documentation:** [`fin-core/fin-ledger/LEDGER.md`](fin-core/fin-ledger/LEDGER.md) —
package layout, the Ledger aggregate, `FinancialFact` normalization, the double-entry model,
Chart of Accounts, accounting periods, Trial Balance, reversal/adjustment, lifecycle, validation,
extension points, configuration, and a usage example.

## At a glance

| Concern | Key types |
|---|---|
| Input contract | `FinancialFact` (Money, `Reference`, `FinancialFactType`, `OccurredAt`, metadata) |
| Posting | `PostingEngine` — the one real top-level facade (see [SPRING.md](SPRING.md)) |
| Chart of Accounts | `AccountTypeRegistry` (ships **empty** — you register your own account types) |
| Posting rules | `PostingRuleRegistry`/`PostingPolicy` (ships **empty** — you register your own rules) |
| SPI registration | `io.genfin.ledger.spi.LedgerExtensions.registerDefaults(registry)` |

## Integration note

`PostingPolicy`/`PostingRuleRegistry` are empty by design — Gen-Fin ships no built-in "invoice
paid → debit Cash / credit AR" mapping. A FIN-SVC deployment must author its own posting-rule
implementation before the Ledger does anything useful; this is the stated "no hardcoded business
rules" philosophy, not a missing API. See [INTEGRATION.md](INTEGRATION.md).

## See also

[ARCHITECTURE.md](ARCHITECTURE.md), [EXTENSIONS.md](EXTENSIONS.md), [INTEGRATION.md](INTEGRATION.md),
[SPRING.md](SPRING.md).
