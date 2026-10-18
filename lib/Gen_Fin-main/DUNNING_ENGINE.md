# Dunning Engine (`fin-dunning`)

A reusable, receivable-centric (not subscription-centric) Dunning Engine: retry/backoff, reminder,
escalation, business-calendar-aware scheduling, collection rules, and failure classification, all
wrapping a generic `FinancialObligation` — never an Invoice, Subscription, or Payment directly.
Depends only on `fin-api` and `fin-money`.

**Full documentation:** [`fin-core/fin-dunning/DUNNING.md`](fin-core/fin-dunning/DUNNING.md) —
why receivable-centric, package layout, the `FinancialObligation`/`DunningCase` aggregate, retry/
reminder/escalation flows, business calendar, collection rules, policy engine, configuration,
extension points, and a usage example wrapping three different domain concepts as one obligation.

## At a glance

| Concern | Key types |
|---|---|
| Input | `FinancialObligation` (backed by its own `Reference` type — see [INTEGRATION.md](INTEGRATION.md)) |
| Case | `DunningCase` (constructed directly from a `FinancialObligation`) |
| Retry/backoff | `RetryPolicy`, `BackoffStrategy` (ships example fixed-interval config) |
| Escalation | `EscalationPolicy`, `EscalationStrategy` |
| Policies | `PolicyRegistry` (ships **empty** — you register your own `DunningPolicy`) |
| SPI registration | `io.genfin.dunning.spi.DunningExtensions.registerDefaults(registry)` |

## Integration note

Every registered default (`BackoffStrategy`, `RetryPolicy`, `SchedulePolicy`, `ReminderPolicy`,
`EscalationPolicy`) is an example/all-defaulted instance, never a hardcoded business interval or
escalation ladder — a real deployment resolves its actual policy through a `DunningPolicy` it
builds and registers itself. `genfin.dunning.enabled` (see [SPRING.md](SPRING.md)) is a
Spring-only gate on the whole module's autoconfiguration; fin-dunning itself has no such field.

## See also

[ARCHITECTURE.md](ARCHITECTURE.md), [EXTENSIONS.md](EXTENSIONS.md), [INTEGRATION.md](INTEGRATION.md),
[SPRING.md](SPRING.md).
