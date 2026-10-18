# fin-refund

`fin-refund` is a reusable Java domain module modeling refunds as an **independent aggregate**,
not a sub-object of `Payment`. It reuses `PaymentGateway.refund(GatewayRequest)` from
`fin-payment`/`fin-provider-api` for the actual money movement, and everything else
(lifecycle, calculation, policy, approval, validation, reasons) is its own SPI-driven model,
mirroring the package conventions established in `fin-payment`.

This document covers the lifecycle, the partial/multiple-refund flow, the approval workflow,
gateway interaction, extension points, configuration, and how an application integrates with
`fin-refund` without ever modifying `fin-payment`.

## Package layout

Same convention as `fin-payment`:

- `io.genfin.refund.<concept>` — public model/value types (`refund`, `request`, `attempt`,
  `lifecycle`, `calculation`, `policy`, `approval`, `reason`, `event`, `config`, `gateway`,
  `serialization`, `validation`, `metadata`, `reference`, `id`, `exception`).
- `io.genfin.refund.port.<concern>` — SPI interfaces per concern (`port.lifecycle`,
  `port.calculation`, `port.policy`, `port.reason`, `port.validation`, `port.gateway`,
  `port.refund`).
- `io.genfin.refund.internal.<concern>` — default implementations, never exported from
  `module-info.java`.
- `io.genfin.refund.spi.RefundExtensions` — registers every default extension into an
  `ExtensionRegistry` in one call.

## The Refund aggregate

`Refund` extends `AggregateRoot<RefundId>`. It is constructed with an amount, a `RefundType`
(`FULL`, `PARTIAL`, `GOODWILL`, `CHARGEBACK_REVERSAL`), a `RefundDirection`
(`OUTBOUND`/`INBOUND`), and a `Reference` to the payment being refunded. That payment reference
is mandatory and must be of `StandardReferenceType.PAYMENT` — the constructor rejects anything
else. Additional references (invoice, customer, ...) can be attached afterwards via
`addReference`, using the same generic `Reference` model `fin-payment` uses, but as fin-refund's
own copy (`io.genfin.refund.reference.Reference`) so the two modules never share a reference
type.

`Refund` never references `Payment` the class, `PaymentId`, or any `fin-payment` internal
type — only the opaque `Reference`. That is what makes it independent: a `Refund` can be
persisted, queried, and reasoned about with no `fin-payment` object graph in memory.

## Lifecycle

```
REQUESTED --SUBMIT_FOR_APPROVAL--> PENDING_APPROVAL --APPROVE--> APPROVED --PROCESS--> PROCESSING
REQUESTED --CANCEL--> CANCELLED
PENDING_APPROVAL --REJECT--> REJECTED
PENDING_APPROVAL --CANCEL--> CANCELLED
APPROVED --CANCEL--> CANCELLED
PROCESSING --COMPLETE--> COMPLETED
PROCESSING --PARTIALLY_COMPLETE--> PARTIALLY_PROCESSED
PROCESSING --FAIL--> FAILED
PARTIALLY_PROCESSED --COMPLETE--> COMPLETED
PARTIALLY_PROCESSED --FAIL--> FAILED
PARTIALLY_PROCESSED --REVERSE--> REVERSED
FAILED --PROCESS--> PROCESSING            (retry)
COMPLETED --REVERSE--> REVERSED
COMPLETED --DISPUTE--> DISPUTED
DISPUTED --REVERSE--> REVERSED
```

The transition table lives in `internal.lifecycle.DefaultRefundLifecycleProvider`, built on
`io.genfin.api.statemachine.StateMachine`. `Refund` holds no transition logic of its own — it
just calls `lifecycle.fire(event)` and throws `IllegalRefundStateTransitionException` when a
transition isn't allowed. Every lifecycle method (`submitForApproval`, `approve`, `reject`,
`startProcessing`, `completeProcessing`, `partiallyComplete`, `fail`, `cancel`, `reverse`,
`dispute`) records a matching domain event (`RefundRequested`, `RefundApproved`, ...) via
`registerEvent`, retrievable with `pullEvents()`.

Swap the whole lifecycle by implementing `port.lifecycle.RefundLifecycleProvider` and
registering it in place of `RefundLifecycles.standard()` — no code in `Refund` itself changes.

## Partial and multiple refunds

A single payment can have many `Refund` aggregates against it — one per partial refund, or one
full refund. Each `Refund` is independent and completes through its own lifecycle instance.
`RefundCalculator` (`port.calculation.RefundCalculator`, default in
`internal.calculation.DefaultRefundCalculator`) is what keeps the payment-level picture
consistent:

- `calculateBalance(paymentTotal, refundsApplied)` sums every refund already applied and returns
  a `RefundBalance` with `totalRefunded()`, `refundableRemaining()`, `isPartiallyRefunded()`, and
  `isFullyRefunded()`.
- `calculate(RefundCalculationContext)` evaluates one new request against that balance, capping
  the `approvedAmount` at whatever remains (`wasCapped()` tells you if it did) rather than ever
  over-refunding.
- `allocate(refundableTotal, requestedByRefund)` splits a fixed refundable total across several
  pending refund requests in insertion order, useful when multiple partial refund requests are
  queued against the same payment concurrently.

Example — two partial refunds against a $100 payment:

```java
RefundCalculator calculator = RefundCalculators.standard();

RefundBalance afterFirst = calculator.calculateBalance(
    Money.of("100.00", USD), List.of(Money.of("30.00", USD)));
// afterFirst.refundableRemaining() == 70.00

RefundCalculationResult second = calculator.calculate(new RefundCalculationContext(
    Money.of("100.00", USD), List.of(Money.of("30.00", USD)), Money.of("90.00", USD)));
// second.approvedAmount() == 70.00, second.wasCapped() == true
```

## Refund window and policy

`RefundPolicy` (`port.policy.RefundPolicy`) composes four independently pluggable sub-policies,
evaluated against a `RefundPolicyContext` (payment total, prior refunds, requested amount,
reason, payment date, evaluation time):

- `RefundWindowPolicy` — rejects requests once a configured `Duration` has elapsed since the
  payment date (`DefaultRefundWindowPolicy`). The boundary itself is inclusive: a request made
  exactly `window` after the payment date is still permitted; one nanosecond later is not.
- `MaximumRefundPolicy` — caps how much may ever be refunded against a payment, independent of
  the payment total itself (`DefaultMaximumRefundPolicy`).
- `ReasonPolicy` — requires the refund reason to be registered in a `RefundReasonRegistry`
  (`DefaultReasonPolicy`).
- `ApprovalPolicy` — flags whether a request requires human approval at all, typically by
  amount threshold (`DefaultApprovalPolicy`).

`RefundPolicyDecision` reports `isPermitted()`, the list of `PolicyViolation`s (each carrying a
`RefundErrorCode`), and `approvalRequired()`.

## Approval workflow

Whether approval is *required* (`ApprovalPolicy`) is a separate concern from *how many*
approvals are needed (`ApprovalWorkflow`). `port.policy.ApprovalWorkflow.requirementFor(context)`
returns an `ApprovalRequirement` (`requiredApprovals()`, `isAutomatic()`,
`isSatisfiedBy(distinctApprovals)`). Built-in workflows, all in `RefundPolicies`:

- `automaticApprovalWorkflow()` — zero approvals ever required.
- `manualApprovalWorkflow()` / `singleApprovalWorkflow()` — exactly one.
- `twoPersonApprovalWorkflow()` — maker-checker, two distinct approvals.
- `thresholdApprovalWorkflow(NavigableMap<Money, Integer> tiers)` — required count scales with
  the requested amount, e.g. 0 approvals under $100, 1 under $1000, 2 above.

Applications needing a different shape (role-based approval, external workflow engine, ...)
implement `ApprovalWorkflow` directly and register it — `Refund`'s own `approve()` transition
doesn't count approvals itself; that bookkeeping belongs to the calling application, which
decides when `ApprovalRequirement.isSatisfiedBy(...)` is finally true and only then fires
`approve()`.

## Gateway interaction — reusing `PaymentGateway`, never a new interface

`fin-refund` never defines `StripeRefundGateway`, `RazorpayRefundGateway`, or any
provider-specific refund port. The *only* gateway interface used is
`io.genfin.payment.port.gateway.PaymentGateway`, specifically its existing
`refund(GatewayRequest)` method. `RefundGatewayOperation.execute(gateway, RefundGatewayRequest)`
is the one place fin-refund's own request/response types
(`gateway.RefundGatewayRequest`/`gateway.RefundGatewayResponse`) cross over to fin-payment's
generic `GatewayRequest`/`GatewayResponse`:

```java
RefundGatewayRequest request = new RefundGatewayRequest(
    refund.id(), refund.amount(), paymentMethod, paymentReference, metadata);

RefundGatewayResponse response = RefundGatewayOperation.execute(paymentGateway, request);
if (response.success()) {
  refund.startProcessing(clock);
  refund.completeProcessing(clock);
} else {
  refund.fail(response.failure().orElseThrow().reason(), clock);
}
```

`port.gateway.RefundGatewayExecutor` wraps that call as an extension point (retries, logging,
metrics) without ever introducing a provider-specific type; `RefundGatewayExecutors.standard()`
is a direct pass-through (`DefaultRefundGatewayExecutor`).

## Extension points (SPI)

Every pluggable concern is an `Extension` (from `fin-api`) registered through an
`ExtensionRegistry`:

| Concern | Port | Default |
|---|---|---|
| Lifecycle | `port.lifecycle.RefundLifecycleProvider` | `RefundLifecycles.standard()` |
| Calculation | `port.calculation.RefundCalculator` | `RefundCalculators.standard()` |
| Refund window | `port.policy.RefundWindowPolicy` | `RefundPolicies.defaultWindowPolicy()` |
| Maximum refund cap | `port.policy.MaximumRefundPolicy` | `RefundPolicies.defaultMaximumRefundPolicy()` |
| Reason eligibility | `port.policy.ReasonPolicy` | `RefundPolicies.reasonPolicy(registry)` |
| Approval requirement | `port.policy.ApprovalPolicy` | `RefundPolicies.defaultApprovalPolicy()` |
| Approval workflow | `port.policy.ApprovalWorkflow` | `RefundPolicies.manualApprovalWorkflow()` |
| Composite policy | `port.policy.RefundPolicy` | `RefundPolicies.standard(registry)` |
| Reason catalog | `port.reason.RefundReasonProvider` / `RefundReasonRegistry` | `RefundReasonRegistries.standardCatalog()` |
| Refund validation | `port.validation.RefundValidator` | `RefundValidators.standard(registry)` |
| Request validation | `port.validation.RefundRequestValidator` | `RefundRequestValidators.standard(registry)` |
| Gateway execution | `port.gateway.RefundGatewayExecutor` | `RefundGatewayExecutors.standard()` |
| Refund construction | `port.refund.RefundBuilderProvider` | `RefundBuilders.standard()` |

`RefundExtensions.registerDefaults(registry)` registers all of the above in one call.
Applications override any single one by registering their own implementation of the port
interface **after** calling `registerDefaults`, or by building a custom `RefundConfiguration`
(see below) and never calling `registerDefaults` at all.

## Configuration

`RefundConfiguration` (in `io.genfin.refund.config`) is the explicit, fully-populated policy set
for a deployment — every field is validated non-null (or non-negative, for `maxRetryAttempts`)
at build time, so a misconfigured deployment fails fast rather than NPEing later:

```java
RefundConfiguration configuration = RefundConfiguration.builder()
    .lifecycleProvider(RefundLifecycles.standard())
    .approvalPolicy(RefundPolicies.approvalPolicy(Money.of("500.00", USD)))
    .approvalWorkflow(RefundPolicies.twoPersonApprovalWorkflow())
    .windowPolicy(RefundPolicies.windowPolicy(Duration.ofDays(90)))
    .maximumRefundPolicy(RefundPolicies.defaultMaximumRefundPolicy())
    .reasonPolicy(RefundPolicies.reasonPolicy(reasonRegistry))
    .calculator(RefundCalculators.standard())
    .validator(RefundValidators.standard(reasonRegistry))
    .requestValidator(RefundRequestValidators.standard(reasonRegistry))
    .maxRetryAttempts(5)
    .build();
```

`RefundConfigurations.standard()` returns the boring, out-of-the-box configuration: standard
lifecycle, no approval threshold, single-approver workflow, 180-day window, uncapped maximum,
standard reason catalog, 3 max retry attempts.

## How applications integrate — without touching `fin-payment`

1. Depend on `fin-refund` (which transitively brings `fin-api`, `fin-money`, `fin-payment`).
2. Obtain the `PaymentGateway` you already use for payments/authorizations from wherever your
   application wires it (a `GatewayRegistry`/`GatewaySelector` from `fin-payment`, or directly).
   `fin-refund` never constructs or selects gateways itself — it only calls `refund(...)` on
   whichever `PaymentGateway` you hand it via `RefundGatewayOperation`/`RefundGatewayExecutor`.
3. Build a `RefundRequest` (`RefundRequestBuilder`/`RefundRequestFactory`) referencing the
   payment via `Reference.payment(paymentId)`, run it through `RefundPolicy.evaluate(...)`, and
   only construct a `Refund` aggregate once the request is permitted.
4. Drive the `Refund` through its lifecycle methods, calling `RefundGatewayOperation.execute`
   (or your custom `RefundGatewayExecutor`) when transitioning into `PROCESSING`.
5. Persist `Refund` and its `pullEvents()` output using your own application's persistence and
   messaging — `fin-refund` has no persistence, HTTP, or messaging concept of its own by design.

Because every extension point is an `Extension` behind an `ExtensionRegistry`, and the only
touchpoint into fin-payment is the existing `PaymentGateway.refund(GatewayRequest)` method,
applications built on `fin-refund` never need to modify `fin-payment`, `fin-provider-api`,
`fin-stripe`, or `fin-razorpay` to add, change, or replace refund behavior.
