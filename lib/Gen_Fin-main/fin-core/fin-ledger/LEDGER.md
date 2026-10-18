# fin-ledger

`fin-ledger` is a reusable Java Accounting Engine: Chart of Accounts, Journal Engine, Double-Entry
Posting Engine, Balance Engine, Accounting Period Engine, Trial Balance, Financial Statement
models, Reversal Engine, and Adjustment Engine, all in one cohesive module. It **never performs
payments, refunds, or reconciliation** — it consumes already-verified financial facts (e.g. from
`fin-reconciliation`, or directly from `fin-payment`/`fin-refund` once an application considers
them settled) and turns them into balanced journal entries. It is not tied to any payment provider
and hardcodes no business account names or posting rules.

This document covers the architecture, the posting flow, the double-entry model, the chart of
accounts, accounting periods, extension points, how `FinancialFact` normalizes upstream input, and
a short usage example.

## Package layout

Same convention as `fin-refund`/`fin-reconciliation`:

- `io.genfin.ledger.<concept>` — public model/value types: `id`, `ledger` (aggregate), `account`
  (Chart of Accounts), `journal`, `posting`, `balance`, `period`, `lifecycle`, `reversal`,
  `validation`, `trialbalance`, `report`, `calculation`, `config`, `reference` (reused from
  `fin-refund`, see below), `event`, `fact`, `spi`, `serialization`.
- `io.genfin.ledger.port.<concern>` — SPI interfaces per concern (`port.lifecycle`,
  `port.account`, `port.journal`, `port.posting`, `port.balance`, `port.period`,
  `port.reversal`, `port.validation`, `port.trialbalance`, `port.report`, `port.calculation`).
- `io.genfin.ledger.internal.<concern>` — default implementations, never exported from
  `module-info.java`.
- `io.genfin.ledger.spi.LedgerExtensions` — registers every default extension into an
  `ExtensionRegistry` in one call.

## The Ledger aggregate

`Ledger` extends `AggregateRoot<LedgerId>`. It owns a `ChartOfAccountsId` and holds
`JournalEntryId`/`BalanceSnapshotId`/`AccountingPeriodId` references only — never the
`ChartOfAccounts`, `JournalEntry`, `Payment`, `Invoice`, `Refund`, `Settlement`, or
`BankTransaction` objects themselves. `recordJournalEntry(JournalEntry)` refuses anything that
has not reached `StandardLedgerStatus.POSTED` in its own lifecycle — the ledger relies on the
journal entry's SPI-driven lifecycle rather than re-deriving posting rules itself.

`LedgerBuilder` builds the aggregate fluently; mirrors `ReconciliationBuilder`.

## Financial fact normalization (`io.genfin.ledger.fact`)

`FinancialFact` is the one shape the Posting Engine consumes, regardless of whether it originated
from an Invoice, Payment, Refund, or Reconciliation record:

```
FinancialFact(Money amount, Reference reference, FinancialFactType factType,
              OccurredAt occurredAt, Map<String, String> metadata)
```

- `reference` is a generic `io.genfin.refund.reference.Reference` — reused as-is (`fin-refund`
  already exports it publicly), not duplicated.
- `factType` is an opaque `FinancialFactType` code (e.g. `"INVOICE_PAID"`, `"REFUND_COMPLETED"`) —
  Gen-Fin defines no built-in catalog of fact types; a consuming application decides what fact
  types exist and which `PostingStrategy` handles each one.

Whoever sits at the boundary of Invoice/Payment/Refund/Reconciliation is responsible for adapting
that module's own event/result type into a `FinancialFact` before handing it to the Posting Engine
— the Ledger's posting logic never depends on any upstream module's internal event shape.

## Double-entry model and posting flow

A `JournalEntry` (`io.genfin.ledger.journal`) is made up of `JournalLine`s, each an entry against
one `AccountId` on either the debit or credit side. **Total debits equal total credits by
construction**: the constructor itself sums both sides and throws `IllegalArgumentException` for
any unbalanced or single-sided (fewer than two lines) set of lines — this is not a rule callers
must remember to run, an unbalanced `JournalEntry` cannot exist.

Posting a `FinancialFact` into a balanced `JournalEntry` goes through `PostingEngine.post(...)`
(`internal.posting.DefaultPostingEngine`):

1. `PostingPolicy.resolve(fact, context)` picks the registered `PostingStrategy` that
   `supports(fact)` and asks it to `resolve(fact, context)` into a list of `PostingEntry`
   (account + `Debit`/`Credit` side + amount).
2. `PostingValidator.validate(entries, context)` (`PostingValidators.standard()`) checks the
   proposed entries — including the same balanced-invariant check — **before** a `JournalEntry` is
   ever constructed, so a misconfigured `PostingStrategy` that only debits one account fails with
   a clear `IllegalStateException("... unbalanced ...")` rather than silently building a broken
   entry.
3. Only once validation passes does the engine build the `JournalEntry` (which independently
   re-enforces the invariant by construction — defense in depth) with `JournalAttributes
   .systemPosted()` and the fact's `Reference` attached.

`PostingCalculator` is the single place that sums debits/credits and rolls entries up into
per-account `PostingBalance`s (`PostingCalculator.balances`, `.isBalanced`, `.totalDebit`,
`.totalCredit`) — the Trial Balance calculator, `BalancedPostingRule`, and `PostingEngine` all
delegate to it rather than each re-implementing the arithmetic.

## Chart of Accounts

`ChartOfAccounts` (`io.genfin.ledger.account`) holds `Account`s keyed by both `AccountId` and an
application-assigned `code` (unique per chart). `Account.type()` is an application-supplied
`AccountType` (never a Gen-Fin enum of "Cash"/"Revenue"/"Tax"/etc — those are illustrative
examples only) classified by `AccountClassification` (asset/liability/equity/revenue/expense).
`AccountHierarchy`/`AccountTree` derive parent-child structure from `Account.parentId()`.
`AccountTypeRegistry` (`port.account`) is the SPI an application registers its own `AccountType`
catalog through; the default (`AccountTypeRegistries.empty()`) is intentionally empty.

## Accounting periods

`AccountingPeriod` (`io.genfin.ledger.period`) is an immutable `[start, end)` window carved out of
a `FiscalYear` at a `PeriodGranularity` (`MONTHLY`/`QUARTERLY`/`YEARLY`, or a custom one). Closing,
locking, or archiving a period never mutates it — `close()`/`reopen()`/`lock()`/`archive()` each
return a **new** `AccountingPeriod` with the new `PeriodStatus`, following
`OPEN → CLOSED → LOCKED → ARCHIVED` (a `CLOSED` period may still be `reopen()`ed back to `OPEN`).
`PeriodCalculator` (default `DefaultPeriodCalculator`, driven by a swappable `PeriodPolicy`)
derives periods from an instant, steps `next()`/`previous()`, and lists `periodsBetween(...)`.
`PeriodValidator` checks a posting falls inside an open period and that a set of periods carries
no overlaps. `JournalEntry` itself carries no period reference — which entries belong to which
period is left to whichever selection an application already uses (e.g. `ValidationContext
.period()`).

## Trial Balance

`TrialBalanceCalculator.calculate(period, entries, currency)` (default
`DefaultTrialBalanceCalculator`) rolls up every `JournalLine` of every entry that has reached
`POSTED`, per account, into a `TrialBalance` — a list of `TrialBalanceEntry` (per-account total
debit/credit) plus a grand-total `TrialBalanceSummary`. It is a pure derived read model: recomputed
from posted entries on demand, never persisted or mutated. **Total debits equal total credits
across the whole trial balance** by the same arithmetic every posted entry already guarantees;
`TrialBalanceValidator.validate(trialBalance)` re-checks that summary arithmetic and reports a
`TRIAL_BALANCE_UNBALANCED` issue if it ever disagrees (defense in depth, not something a
correctly-posted ledger can trigger).

## Reversal and Adjustment

Corrections and reversals **never delete or mutate a journal entry** — `ReverseJournal`
(`port.reversal`, default `DefaultReverseJournal`) always creates a new entry alongside the
original:

- `reverse(original, reason, memo, at)` fires `original.reverse()` (a forward lifecycle
  transition to `REVERSED`, not a deletion) and returns a `Reversal` carrying a brand-new
  `reversingEntry` — every line's debit/credit side swapped, same accounts, same amounts. The
  original's lines (`original.lines()`) are unchanged before and after.
- `adjust(original, correctiveLines, reason, memo, at)` posts the supplied corrective
  `PostingEntry` legs as a new `adjustingEntry` and fires `original.adjust()` (→ `ADJUSTED`).
- `correct(original, replacementLines, reason, memo, at)` combines both: reverses the original,
  then posts the replacement lines as a brand-new `STANDARD` entry — three entries end up existing
  side by side (original, reversal, correction), none of them ever removed.

`ReversalPolicy` decides whether a given entry/reason combination is reversible at all (an entry's
`JournalAttributes` can mark itself not-reversible); `ReversalReasonRegistry` is the SPI catalog of
registered `ReversalReason`s an unregistered reason is rejected against.

## Lifecycle

Each `JournalEntry` carries its own posting lifecycle (a ledger holds many entries, each
progressing independently), driven entirely by the SPI-replaceable
`port.lifecycle.LedgerLifecycleProvider` — `JournalEntry` holds no transition table of its own.

```
CREATED --VALIDATE--> VALIDATED --POST--> POSTED --SETTLE--> SETTLED
CREATED --FAIL--> FAILED
VALIDATED --FAIL--> FAILED
POSTED --REVERSE--> REVERSED
POSTED --ADJUST--> ADJUSTED
SETTLED --REVERSE--> REVERSED
SETTLED --ADJUST--> ADJUSTED
SETTLED --ARCHIVE--> ARCHIVED
ADJUSTED --ARCHIVE--> ARCHIVED
ADJUSTED --REVERSE--> REVERSED
REVERSED --ARCHIVE--> ARCHIVED
FAILED --RETRY--> VALIDATED
```

Every transition is appended to the entry's `JournalHistory` (append-only) and bumps its
`JournalVersion` — a rejected transition throws `IllegalStateException` and leaves both untouched.

## Validation

`JournalValidator` (default `DefaultJournalValidator`) runs every registered `ValidationRule` and
**collects every issue in one pass — it never short-circuits**, mirroring
`DefaultReconciliationValidator`. Default rules (`Validators.defaultRules()`):

| Rule | Rule code | Fires when |
|---|---|---|
| `BalancedPostingRule` | `UNBALANCED_JOURNAL_ENTRY` | Total debits ≠ total credits (defense in depth — `JournalEntry` already prevents this by construction). |
| `AccountExistsRule` | `ACCOUNT_NOT_FOUND` / `ACCOUNT_NOT_POSTABLE` | An account the entry posts against is missing from, or closed for posting in, the supplied `ChartOfAccounts`. |
| `PeriodOpenRule` | `PERIOD_NOT_OPEN` | The supplied `AccountingPeriod` is not `OPEN`. |
| `CurrencyCompatibilityRule` | `MULTIPLE_CURRENCIES_IN_ENTRY` | The entry's lines don't all use one currency. |
| `MoneyValidationRule` | `INVALID_MONEY_AMOUNT` | A line's active side is not strictly positive. |
| `ReferenceValidationRule` | `MISSING_REFERENCE` | The entry carries no `Reference` back to the fact that caused it. |
| `PostingRuleMatchedRule` | `POSTING_RULE_NOT_MATCHED` | The entry did not originate from a registered `PostingRule`. |

Rules that need a `ChartOfAccounts`/`AccountingPeriod`/posting-rule-matched flag on
`ValidationContext` simply report no issue when the context leaves that field unset — validation
degrades gracefully rather than requiring every caller to populate every field.

## Extension points (SPI)

Every pluggable concern is registered through an `ExtensionRegistry`:

| Concern | Port | Default |
|---|---|---|
| Lifecycle | `port.lifecycle.LedgerLifecycleProvider` | `LedgerLifecycles.standard()` |
| Account types | `port.account.AccountTypeRegistry` | `AccountTypeRegistries.empty()` (application-registered) |
| Journal building | `port.journal.JournalBuilderProvider` | `JournalBuilders.standard()` |
| Posting strategy/policy | `port.posting.PostingStrategy` / `PostingPolicy` | `PostingPolicies.empty()` (application-registered) |
| Posting rule registry/resolver | `port.posting.PostingRuleRegistry` / `PostingRuleResolver` | `PostingRuleRegistries.empty()` (application-registered) |
| Posting validation | `port.posting.PostingValidator` | `PostingValidators.standard()` |
| Posting engine | `port.posting.PostingEngine` | `PostingEngines.of(policy, validator)` |
| Balance policy/calculator | `port.balance.BalancePolicy` / `BalanceCalculator` | `BalanceCalculators.standardPolicy()` / `.of(policy)` |
| Period policy/calculator | `port.period.PeriodPolicy` / `PeriodCalculator` | `PeriodCalculators.standardPolicy()` / `.of(policy)` |
| Trial balance | `port.trialbalance.TrialBalanceCalculator` | `TrialBalanceCalculators.standard()` |
| Reversal reasons/policy | `port.reversal.ReversalReasonRegistry` / `ReversalPolicy` | `ReversalReasonRegistries.standardCatalog()` / `ReversalPolicies.standard()` |
| Reverse/adjust/correct | `port.reversal.ReverseJournal` | `ReverseJournals.standard()` |
| Validation rules | `io.genfin.ledger.validation.ValidationRule` | `Validators.defaultRules()` |
| Journal validation | `port.validation.JournalValidator` | `Validators.standard()` |
| Report formatting | `port.report.ReportFormatter` | `ReportFormatters.standard()` |
| Account/variance calculation | `port.calculation.AccountCalculator` / `VarianceCalculator` | `AccountCalculators.standard()` / `VarianceCalculators.standard()` |

`LedgerExtensions.registerDefaults(registry)` registers all of the above in one call. Account
types and posting-rule mappings register **empty** by design — Gen-Fin never hardcodes business
account names ("Cash", "Revenue", "Tax", "Escrow", ...) or posting mappings ("Invoice Paid → Debit
Cash / Credit AR", ...); a consuming application registers its own `AccountType` catalog and
`PostingStrategy`/`PostingRuleSet`s before posting a single fact.

## Configuration

`LedgerConfiguration` (`io.genfin.ledger.config`) is the explicit, fully validated policy set for
a deployment, composed of five sub-configurations (`PostingConfiguration`, `BalanceConfiguration`,
`PeriodConfiguration`, `ReportingConfiguration`, `ValidationConfiguration`) alongside the
lifecycle/account-registry/journal-builder/reversal policies — every field is validated non-null
at build time:

```java
LedgerConfiguration configuration = LedgerConfiguration.builder()
    .lifecycleProvider(LedgerLifecycles.standard())
    .accountTypeRegistry(myAccountTypeRegistry)          // application-supplied, not empty
    .journalBuilderProvider(JournalBuilders.standard())
    .reversalPolicy(ReversalPolicies.standard())
    .reverseJournal(ReverseJournals.standard())
    .postingConfiguration(PostingConfiguration.builder()
        .policy(PostingPolicies.of(myPostingStrategies)) // application-supplied, not empty
        .validator(PostingValidators.standard())
        .ruleRegistry(myPostingRuleRegistry)
        .ruleResolver(PostingRuleResolvers.of(myPostingRuleRegistry))
        .engine(PostingEngines.of(myPostingPolicy, PostingValidators.standard()))
        .build())
    .balanceConfiguration(BalanceConfiguration.builder()
        .policy(BalanceCalculators.standardPolicy())
        .calculator(BalanceCalculators.of(BalanceCalculators.standardPolicy()))
        .build())
    .periodConfiguration(PeriodConfiguration.builder()
        .policy(PeriodCalculators.standardPolicy())
        .calculator(PeriodCalculators.of(PeriodCalculators.standardPolicy()))
        .build())
    .reportingConfiguration(ReportingConfiguration.builder()
        .trialBalanceCalculator(TrialBalanceCalculators.standard())
        .formatter(ReportFormatters.standard())
        .build())
    .validationConfiguration(ValidationConfiguration.builder()
        .journalValidator(Validators.standard())
        .build())
    .build();
```

`LedgerConfigurations.standard()` returns the out-of-the-box configuration built from the defaults
above — its posting policy and posting rule registry both start **empty**, since Gen-Fin registers
no account names or posting mappings of its own.

## Usage example

```java
Currency usd = CurrencyFactory.newCurrency().code("USD").symbol("$")
    .displayName("US Dollar").build();

AccountId cash = AccountId.generate();
AccountId receivable = AccountId.generate();

// Application-registered posting rule: "INVOICE_PAID" -> Debit Cash / Credit Receivable.
PostingStrategy invoicePaid = new PostingStrategy() {
  public boolean supports(FinancialFact fact) {
    return "INVOICE_PAID".equals(fact.factType().code());
  }
  public List<PostingEntry> resolve(FinancialFact fact, PostingContext context) {
    return List.of(
        PostingEntry.of(cash, Debit.of(fact.amount())),
        PostingEntry.of(receivable, Credit.of(fact.amount())));
  }
};

PostingEngine engine = PostingEngines.of(
    PostingPolicies.of(List.of(invoicePaid)), PostingValidators.standard());

// A financial fact normalized from an upstream Invoice/Payment event at the module boundary.
FinancialFact fact = FinancialFact.of(
    Money.of("100.00", usd),
    Reference.payment("PAY-1"),
    FinancialFactType.of("INVOICE_PAID"),
    new OccurredAt(Instant.now()));

Ledger ledger = new Ledger(LedgerId.generate(), ChartOfAccountsId.generate());

JournalEntry entry = engine.post(fact, ledger.id(), PostingContext.at(Instant.now()));
entry.validate();
entry.post();
ledger.recordJournalEntry(entry);

// Later: derive a trial balance for the period and confirm it balances.
TrialBalance trialBalance = TrialBalanceCalculators.standard()
    .calculate(currentPeriod, List.of(entry), usd);
assert TrialBalanceValidator.validate(trialBalance).isValid();
```

## How applications integrate — without touching payment/refund/reconciliation/money

1. Depend on `fin-ledger` (which transitively brings `fin-api`, `fin-money`, `fin-payment`,
   `fin-refund`, `fin-reconciliation`).
2. Register your own `AccountType` catalog and `Account`s in a `ChartOfAccounts`, and your own
   `PostingStrategy`/`PostingRuleSet`s for the fact types your application produces —
   `fin-ledger` supplies no accounts or posting mappings of its own.
3. At the boundary of Invoice/Payment/Refund/Reconciliation, adapt each module's own event/result
   type into a `FinancialFact` using only its published public API (`Reference`, `Money`, id
   types) — never its internal packages.
4. Run `PostingEngine.post(fact, ledgerId, context)` to get a balanced `JournalEntry`, drive it
   through `validate()`/`post()`/`settle()`, and record it on your `Ledger` aggregate.
5. Derive `Balance`s, `TrialBalance`s, and reports on demand from posted entries — `fin-ledger` has
   no persistence, HTTP, or messaging concept of its own; storing entries and driving this flow is
   the consuming application's responsibility.

Because every extension point is registered through an `ExtensionRegistry` and the only touchpoint
into `fin-refund` is its existing public `Reference`/`ReferenceCollection` type, applications built
on `fin-ledger` never need to modify `fin-payment`, `fin-refund`, `fin-reconciliation`, or
`fin-money` to add, change, or replace accounting behavior.
