# fin-dunning

`fin-dunning` is a reusable Java **receivable-centric collection engine** — not a subscription-
billing engine. Its central domain object is `FinancialObligation`, never `Invoice` or
`Subscription`. It plans reminders, retries, and escalations for *any* amount of money owed by a
due date: an overdue invoice, a subscription renewal charge, a marketplace settlement payable, a
loan installment, an EMI, a healthcare bill, an education fee, a vendor payment. A consuming
application wraps its own domain concept as a `FinancialObligation` to use this engine — fin-dunning
never assumes which kind of obligation it is looking at.

`fin-dunning` **produces plans and decisions only — it never executes them.** It never sends an
email/SMS, never suspends a service, never freezes an account, never writes anything off. Every
output type (`ReminderPlan`, `RetryPlan`, `EscalationDecision`, `CollectionDecision`) is data
describing what *should* happen; acting on it is entirely the consuming application's
responsibility (its own notification sender, its own scheduler, its own account/service state).

## Why receivable-centric, not subscription-centric

The pipeline this module implements is fixed in shape, not in content:

```
FinancialObligation -> Policy Resolution -> Reminder Plan -> Retry Plan -> Escalation Plan -> Completion/Write-off
```

Nothing about "renewal", "invoice", or "installment" appears anywhere in that pipeline — those are
all just `ObligationType`s an application defines for itself (`ObligationType` is a one-method SPI
interface, not a closed enum). The same `RetryPolicy`/`BackoffStrategy`/`BusinessCalendar` engine
that retries a failed subscription charge equally retries an overdue invoice reminder cadence or a
missed loan installment — the only difference is which policy an application resolves for which
`ObligationType`.

## Package layout

Same convention as `fin-ledger`/`fin-pricing`:

- `io.genfin.dunning.<concept>` — public model/value types: `id`, `reference`, `obligation`
  (`FinancialObligation`), `dunning` (`DunningCase` aggregate), `collection` (`CollectionPlan`/
  `CollectionStage`/`CollectionHistory`), `lifecycle`, `calendar` (Business Calendar), `backoff`,
  `retry`, `schedule`, `reminder`, `notification`, `escalation`, `policy`, `rule` (Collection
  Rules), `failure` (Failure Classification), `validation`, `config`, `event`, `spi`,
  `serialization`.
- `io.genfin.dunning.port.<concern>` — SPI interfaces per concern (`port.lifecycle`,
  `port.calendar`, `port.backoff`, `port.retry`, `port.schedule`, `port.reminder`,
  `port.notification`, `port.escalation`, `port.policy`, `port.rule`, `port.failure`,
  `port.validation`).
- `io.genfin.dunning.internal.<concern>` — default implementations, never exported from
  `module-info.java`.
- `io.genfin.dunning.spi.DunningExtensions` — registers every default extension into an
  `ExtensionRegistry` in one call.

## The FinancialObligation and DunningCase aggregate

`FinancialObligation` (`io.genfin.dunning.obligation`) is the one shape every kind of receivable
fits through: an `amount` (`Money`, never `BigDecimal`), a `dueDate`, an application-supplied
`ObligationType`, and a `Reference` back to whatever the application's real record is (invoice,
subscription renewal, loan installment, ...). `isOverdueAsOf(Instant)` is its only behavior;
`FinancialObligationBuilder` builds it fluently.

`DunningCase` (`io.genfin.dunning.dunning`) is the aggregate that tracks one obligation's collection
lifecycle: it holds the `FinancialObligation`, its resolved `CollectionPlan`, its current
`CollectionStage`, an append-only `CollectionHistory`, and its own `StandardDunningCaseStatus`
lifecycle (driven by the SPI-replaceable `port.lifecycle.DunningCaseLifecycleProvider`, not a
hardcoded transition table). It never mutates its `FinancialObligation`; `advanceTo(stage, ...)`
rejects any stage not part of its plan, and every mutation appends a domain event
(`DunningCaseOpened`, `DunningStarted`, `ReminderScheduled`/`ReminderGenerated`,
`RetryPlanned`/`RetryExecuted`, `EscalationTriggered`, `DunningCaseStageAdvanced`,
`CollectionCompleted`/`CollectionFailed`/`CollectionWrittenOff`, `DunningCaseClosed`) to its pending
event queue, drained via `pullEvents()`. `DunningCaseBuilder`/`DunningCaseFactory` build it fluently
— the factory additionally resolves the lifecycle provider from an `ExtensionRegistry`.

## Retry flow

Nothing about a retry interval, unit, or attempt count is ever hardcoded outside example
defaults — it is always resolved through this chain:

```
RetryPolicy { BackoffStrategy, BusinessCalendar, ZoneId, maxRetries }
   -> RetryStrategy.decide(attemptNumber, from, policy) -> RetryDecision
      -> RetryCalculator.plan(policy, from) drives attempts 1..maxRetries+1 -> RetryPlan
```

- `BackoffStrategy` (`port.backoff`) computes the delay before attempt N from a reference instant.
  `BackoffStrategies` ships **fixed / linear / exponential / fibonacci** shapes, every one of them
  parameterized by an application-supplied `BackoffInterval(amount, ChronoUnit)` — minutes, hours,
  days, weeks are all equally supported, nothing is baked in beyond the shape of the curve itself.
  An application implements `BackoffStrategy` directly for any other curve.
- `BusinessCalendar` (`port.calendar`) rolls a candidate date onto an eligible business day per a
  `WeekendStrategy` and `HolidayProvider` — see [Business calendar](#business-calendar) below.
- `RetryCalculator.plan(...)` is bounded at `maxRetries() + 1` attempts regardless of what a
  misbehaving custom `RetryStrategy` reports, so it can never loop forever even if a
  faulty extension never signals exhaustion.
- `RetryPlan` is the full ordered list of `RetryDecision`s (`scheduled(attemptNumber, window)` or
  `exhausted(attemptNumber)`) for one obligation — data only, `fin-dunning` never fires a retry.

`RetryHistory` (`io.genfin.dunning.retry`) records what actually happened per attempt
(`RetryExecution` + `RetryResult`) after the consuming application executes a planned attempt and
reports the outcome back — fin-dunning never executes the attempt itself.

## Reminder flow

`ReminderPolicy` resolves a `ReminderSchedule` made of `ReminderRule`s (occurrence number → channel
+ template reference); `ReminderStrategy` turns that schedule plus a reference instant into a
`ReminderPlan` of `Reminder`s, each carrying a `ReminderChannel` (`StandardReminderChannel` is an
illustrative, non-exhaustive set — email/SMS/push/letter/in-app are all just codes an application
may extend) and a `TimeWindow` it should fire within. No specific channel or template is ever
chosen by fin-dunning itself — an obligation with no configured rules simply gets no reminders,
which is the deliberate empty-by-default behavior of `ReminderPolicyConfiguration`.

## Escalation flow

`EscalationPolicy` resolves a ladder of `EscalationRule`s (`triggerAfterAttempts` → `EscalationLevel`
+ `EscalationAction`) — a rule stays in effect for every attempt count at or beyond its threshold
until a higher rung is reached, so an application expresses a ladder with as many or as few rungs as
it needs. `EscalationStrategy.decide(...)` turns the current failed-attempt count into an
`EscalationDecision` (`escalate(attemptNumber, level, action)` or none). `StandardEscalationAction`
(`NOTIFY_MANAGER`, `SUSPEND_SERVICE`, `FREEZE_ACCOUNT`, `WRITE_OFF`, `MANUAL_REVIEW`) is a small,
non-exhaustive illustrative set — **fin-dunning never performs any of these actions**; it only
produces the `EscalationDecision` describing which one the application should carry out.

## Business calendar

`BusinessCalendar` (`port.calendar`, default `DefaultBusinessCalendar`) composes two independently
pluggable SPIs:

- `WeekendStrategy.isWeekend(DayOfWeek)` — `WeekendStrategies.satSun()` (the shipped default) and
  `.fridaySaturday()` are both just examples; an application implements the SPI directly for any
  other working-week shape.
- `HolidayProvider.isHoliday(LocalDate)` — `HolidayProviders.none()` is the *only* default
  fin-dunning ships (no holidays at all), explicitly so an application always plugs in its own
  regional/business calendar for anything beyond that trivial case.

`CalendarPolicy` also carries a `RollConvention` (`FORWARD`/`BACKWARD`, via `RollConventions`) and a
`businessCalendarAware` flag — when `false`, `roll(date)` is a no-op and scheduling reduces to pure
calendar-day arithmetic, for callers that explicitly don't want business-day skipping.
`BusinessCalendar.nextBusinessDay`/`roll`/`addBusinessDays` all route through the same
weekend+holiday check; `RetryWindow.adjusted(...)` is how a candidate retry instant gets rolled onto
an eligible day. `GracePeriod.of(amount, ChronoUnit)` (arbitrary unit, never hardcoded) decides
whether a due date has tipped into dunning-eligibility yet.

## Collection rules and failure classification

`CollectionRuleEngine` (`port.rule`, default `DefaultCollectionRuleEngine`) evaluates every
registered `CollectionRule` against a `CollectionRuleContext` and returns a `CollectionDecision`
(`StandardCollectionOutcome` — e.g. proceed, pause, skip-today). Shipped rules
(`GracePeriodActiveRule`, `HolidaySkipTodayRule`, `MaxRetriesReachedRule`,
`CollectionPausedRule`, `PriorityCollectionRule`) are all data-driven off the resolved policy/
calendar/history, never a literal day count or channel.

`FailureClassifier` (`port.failure`, default `DefaultFailureClassifier`) maps a raw failure signal
into a `FailureCategory` (`StandardFailureCategory` — `TEMPORARY`/`PERMANENT`/`GATEWAY`/`CUSTOMER`/
`FRAUD`/`BUSINESS`, an illustrative, non-exhaustive taxonomy) via registered
`FailureClassificationRule`s, and `FailurePolicy` maps a category to a `FailureDisposition`
(`StandardFailureDisposition` — `RETRY`/`ESCALATE`/`WRITE_OFF`/`MANUAL_REVIEW`/`NO_ACTION`) —
again, data only; fin-dunning never retries, escalates, or writes anything off itself.

## Policy engine

`DunningPolicy` (`port.policy`) is the single top-level policy an application registers for one kind
of receivable, composing `SchedulePolicy` (grace period + `RetryPolicy`), `ReminderPolicy`,
`EscalationPolicy`, and the ordered `CollectionStage` template a case following it progresses
through. `PolicyRegistry` holds registered policies; `PolicyResolver` maps a `FinancialObligation`'s
`ObligationType` to the policy that governs it; `PolicyEvaluator` turns a resolved policy into a
`CollectionPlan` and answers `isDunningDue(policy, obligation, asOf)`. `fin-dunning` ships **no**
built-in policy beyond an all-defaulted example (`DunningPolicies.standard()`) — every real policy
is data an application builds with `PolicyBuilder` and registers itself.

Three fully-worked configuration examples an application might register (illustrative — none of
this is fin-dunning code, it is what a consuming application supplies as data):

**Policy A — invoice overdue collections** (business-calendar-aware, gentle):
- Grace period: 3 days past due date.
- Backoff: fixed, 5 days, skipping weekends/holidays via the application's own `BusinessCalendar`.
- Reminders: occurrence 1 → EMAIL, occurrence 2 → EMAIL + SMS.
- Max retries: 3. Escalation ladder: after attempt 3 → `MANUAL_REVIEW`.

**Policy B — subscription renewal retries** (pure calendar-day, aggressive, short window):
- Grace period: 0 (retry starts immediately at the failed charge instant).
- Backoff: exponential, base 6 hours, multiplier 2.0, `businessCalendarAware = false` (renewals
  retry every calendar hour/day regardless of weekends).
- Reminders: none (a silent retry-only policy — an application is free to configure zero reminder
  rules).
- Max retries: 4. Escalation ladder: after attempt 2 → `NOTIFY_MANAGER`; after attempt 4 →
  `SUSPEND_SERVICE`.

**Policy C — loan installment / EMI collections** (long-running, calendar-aware, structured
ladder):
- Grace period: 5 days.
- Backoff: linear, base 7 days, business-calendar-aware.
- Reminders: occurrence 1 → EMAIL, occurrence 2 → SMS, occurrence 3 → EMAIL + phone-call reference.
- Max retries: 8. Escalation ladder: after attempt 4 → `NOTIFY_MANAGER`; after attempt 6 →
  `FREEZE_ACCOUNT`; after attempt 8 → `WRITE_OFF`.

## Configuration

`DunningConfiguration` (`io.genfin.dunning.config`) is the explicit, fully-validated policy set for
a deployment, composed of the cross-cutting collaborators (resolved `DunningPolicy`, lifecycle
provider, `CollectionRuleEngine`, `FailurePolicy`, `NotificationStrategy`) plus the per-concern
sub-configurations that each carry their own collaborator (`BackoffConfiguration`,
`CalendarConfiguration`, `RetryConfiguration`, `ScheduleConfiguration`, `ReminderConfiguration`,
`EscalationConfiguration`, `ValidationConfiguration`) — every field validated non-null at build
time. `DunningConfigurations.standard()` returns the out-of-the-box configuration built entirely
from example defaults; a real deployment overrides whichever sub-configuration its own policy
needs, never edits fin-dunning source to change a default.

## Extension points (SPI)

Every pluggable concern is registered through an `ExtensionRegistry`:

| Concern | Port | Default |
|---|---|---|
| Case lifecycle | `port.lifecycle.DunningCaseLifecycleProvider` | `DunningCaseLifecycles.standard()` |
| Weekend rule | `port.calendar.WeekendStrategy` | `WeekendStrategies.satSun()` |
| Holidays | `port.calendar.HolidayProvider` | `HolidayProviders.none()` (application-registered) |
| Business calendar | `port.calendar.BusinessCalendar` | `BusinessCalendars.standard()` |
| Backoff | `port.backoff.BackoffStrategy` | `BackoffStrategies.fixed(...)` (example only) |
| Retry policy/strategy | `port.retry.RetryPolicy` / `RetryStrategy` | `RetryPolicies.standard()` / `RetryStrategies.standard()` |
| Schedule policy/strategy | `port.schedule.SchedulePolicy` / `ScheduleStrategy` | `SchedulePolicies.standard()` / `ScheduleStrategies.standard()` |
| Reminder policy/strategy | `port.reminder.ReminderPolicy` / `ReminderStrategy` | `ReminderPolicies.standard()` / `ReminderStrategies.standard()` |
| Notification | `port.notification.NotificationStrategy` | `NotificationStrategies.standard()` |
| Escalation policy/strategy | `port.escalation.EscalationPolicy` / `EscalationStrategy` | `EscalationPolicies.standard()` / `EscalationStrategies.standard()` |
| Collection rules/engine | `port.rule.CollectionRule` / `CollectionRuleEngine` | `CollectionRules.defaultRules()` / `CollectionRuleEngines.standard()` |
| Failure policy/classifier | `port.failure.FailurePolicy` / `FailureClassifier` | `FailurePolicies.standard()` / `FailureClassifiers.standard()` |
| Policy registry | `port.policy.PolicyRegistry` | `PolicyRegistries.empty()` (application-registered) |
| Validation | `port.validation.DunningValidator` | `Validators.standard()` |

`DunningExtensions.registerDefaults(registry)` registers all of the above in one call.
`PolicyRegistry` registers **empty** by design — Gen-Fin defines no built-in dunning policies of
its own; an application always builds and registers its own `DunningPolicy` instances (see Policy
A/B/C above) before resolving anything for a real obligation.

## Usage example — wrapping three different domain concepts as one FinancialObligation

```java
// 1. An invoice-collections application wraps its own overdue invoice balance:
FinancialObligation invoiceObligation = FinancialObligation.of(
    Money.of("249.00", usd),
    invoice.dueDate(),
    myObligationTypes.invoice(),                 // application's own ObligationType
    Reference.obligationSource(invoice.id().value()));

// 2. A subscription-billing application wraps a failed renewal charge:
FinancialObligation renewalObligation = FinancialObligation.of(
    Money.of("19.99", usd),
    renewalAttempt.chargedAt(),
    myObligationTypes.subscriptionRenewal(),
    Reference.obligationSource(subscription.id().value()));

// 3. A lending platform wraps one missed EMI:
FinancialObligation emiObligation = FinancialObligation.of(
    Money.of("4200.00", inr),
    installment.dueDate(),
    myObligationTypes.loanInstallment(),
    Reference.obligationSource(loan.id().value() + "#" + installment.number()));

// Each application registers its own DunningPolicy per ObligationType, then resolves and opens
// a case identically regardless of which kind of obligation it is:
PolicyResolver resolver = PolicyResolvers.of(myPolicyRegistry, myObligationTypeToPolicyIdMapping);
DunningPolicy policy = resolver.resolve(invoiceObligation).orElseThrow();
CollectionPlan plan = new PolicyEvaluator().collectionPlanFor(policy);

DunningCase dunningCase = DunningCaseFactory.open(extensionRegistry, invoiceObligation, plan);

// Ask the engine what should happen next - it decides, the application acts:
RetryPlan retryPlan = new RetryCalculator(RetryEngineFactory.from(extensionRegistry))
    .plan(policy.schedulePolicy().retryPolicy(), invoiceObligation.dueDate());
ReminderPlan reminderPlan = ReminderStrategies.standard()
    .plan(invoiceObligation, dunningSchedule, policy.reminderPolicy(),
        ReminderContext.asOf(invoiceObligation.dueDate()));

// The application - never fin-dunning - sends the reminders, executes the retries, and carries
// out whatever EscalationDecision eventually comes back.
```

Because every extension point is registered through an `ExtensionRegistry` and `fin-dunning`
depends only on `fin-api`, `fin-money`, and `fin-payment`, an application can add, change, or
replace any reminder/retry/escalation/calendar behavior without ever depending on — or requiring a
change to — `fin-invoice`, `fin-refund`, `fin-ledger`, `fin-reconciliation`, `fin-pricing`, or any
provider module.
