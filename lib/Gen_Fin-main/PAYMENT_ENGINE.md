# Payment Engine (`fin-payment`)

A provider-agnostic payment domain — not a Stripe module, not a Razorpay module, not a checkout SDK.
It's the reusable business model every payment provider plugs into via
`io.genfin.payment.port.gateway.PaymentGateway`. No gateway SDKs, HTTP clients, webhooks, or
provider implementations live here. Depends on `fin-api` and `fin-money`; **not** on `fin-invoice` —
an invoice is referenced only through the same opaque `Reference` model Invoice itself uses,
independently re-implemented here (~130 lines) rather than creating a `fin-payment → fin-invoice`
dependency, keeping the two engines loosely coupled as required.

## Package map

```
io.genfin.payment
├── payment/        Payment (aggregate root) + PaymentBuilder, PaymentType/Direction/Purpose, AuditInfo
├── id/             PaymentId, AttemptId, SessionId — strongly-typed, never raw UUIDs
├── reference/      Self-contained Reference/ReferenceType/ReferenceValue/ReferenceCollection
├── metadata/       Lightweight string-keyed PaymentMetadata bag
├── lifecycle/      PaymentState/PaymentEvent + the standard transition table (fin-api StateMachine)
├── method/         PaymentMethod/Type/Descriptor/Capabilities + PaymentMethodRegistry
├── idempotency/    IdempotencyKey/Policy/Result — contracts only, no storage
├── intent/         PaymentIntent + IntentBuilder — the provider-neutral intention to collect funds
├── session/        PaymentSession/State/Expiration/Reference + SessionBuilder
├── attempt/        PaymentAttempt/AttemptResult + AttemptBuilder — every execution attempt, kept
├── authorization/  Authorization/Capture/VoidAction/ReleaseAction + Authorization/CaptureStrategy
├── failure/        FailureCategory/RetryRecommendation/RecoveryAction/FailureReason — generic, no provider codes
├── event/          PaymentCreated/Authorized/Captured/Settled/Failed/Expired/Cancelled/RefundRequested/Refunded/Disputed
├── gateway/        GatewayRequest/Response/Capabilities/Health/Configuration, PaymentProvider/ProviderCapabilities, registry/selector
├── calculation/    PaymentBalance/OutstandingAmount/SettlementResult/PaymentAllocation
├── validation/     ValidationRule/Context/Result + 7 default rules
├── config/         PaymentConfiguration (immutable) + PaymentConfigurations
├── serialization/  ReferenceCodec (see scope note below)
├── spi/            PaymentExtensions — bootstraps defaults into a fin-api ExtensionRegistry
├── exception/      PaymentErrorCode + exception hierarchy
└── port/<feature>/ The pluggable side of every feature above (interfaces only — implemented by
    internal/<feature>/ Hidden default implementations (never exported)   provider modules, e.g. fin-stripe)
```

## Payment lifecycle

`CREATED → PENDING → (Partially) AUTHORIZED → (Partially) CAPTURED → SETTLED`, with
`FAILED`/`CANCELLED`/`EXPIRED` reachable from the early states, `REFUNDED`/`PARTIALLY_REFUNDED` from
any captured/settled state, and `DISPUTED → CHARGEBACK` as a side path off `CAPTURED`/`SETTLED`. The
whole table lives in `DefaultLifecycleProvider`, built entirely on `fin-api`'s generic `StateMachine`
— nothing about transitions is hardcoded in `Payment`. Swap `LifecycleProvider` for a custom table.

## Authorization vs. capture

These are separate, explicit steps — `Payment.authorize(amount, gatewayReference, ...)` records an
`Authorization` and fires `AUTHORIZE`/`PARTIALLY_AUTHORIZE`; `Payment.capture(amount, ...)` is
independent and repeatable (multiple partial captures accumulate against the authorized amount,
validated by `CaptureExceedsAuthorizationException` if they'd exceed it). `release()` records an
uncaptured-remainder release without forcing a lifecycle transition. Whether capture happens
automatically right after authorization, or only on an explicit later call, is entirely the
application's choice via `CaptureStrategy` — the engine has no opinion.

## Payment Intent model

`PaymentIntent` is the pre-payment "intention to collect funds": `Money` amount, invoice/customer
`Reference`s, expiration, `PaymentPurpose`, and an `IdempotencyKey` — no provider concepts, no
Stripe-style `PaymentIntent` fields, no provider ids. A `PaymentGateway` implementation turns one
into a provider-specific call; the intent itself never touches a provider.

## Attempt model

Every execution attempt is a separate, appended `PaymentAttempt` (never overwritten) — timestamp,
amount, `AttemptResult`, an optional `FailureReason`, retry number, and an opaque gateway reference.
`Payment.recordAttempt(...)` does **not** force a lifecycle transition on failure — the caller
decides whether to retry (record another attempt) or give up (`Payment.fail(...)`, which does
transition and fires `PaymentFailed`). This sequence is exactly the basis Dunning/retry logic in a
later phase builds on.

## Gateway architecture

`PaymentGateway` is the single interface every provider module implements: `authorize`/`capture`/
`voidAuthorization`/`refund`, plus `capabilities()`/`health()`/`provider()`. Every parameter and
return type (`GatewayRequest`, `GatewayResponse`, `FailureReason`, ...) is generic — no raw JSON, no
HTTP, no SDK objects cross this boundary in either direction. `GatewayCapabilities` describes what
API *operations* a gateway supports; `ProviderCapabilities` (on the separate `PaymentProvider`
descriptor) describes business-level facts about the organization behind it (supported
currencies/countries, PCI compliance, 3-D Secure) — operational vs. organizational, kept distinct.
`GatewayRegistry` holds configured gateways by opaque id; `GatewaySelector` picks one per request
(the default `firstCapable()` picks the first registered gateway supporting the request's payment
method — replace it for cost-based, region-based, or any other selection logic).

## Idempotency model

Contracts only, no storage. `IdempotencyValidator.validate(key, requestFingerprint,
existingFingerprint)` is a pure function: no existing fingerprint → `NEW`; matching fingerprint →
`REPLAYED`; mismatched → `CONFLICT`. The application owns looking up `existingFingerprint` from
wherever it stores keys (this phase is explicitly persistence-free) and owns what "fingerprint"
means (a hash of the request body, typically).

## Partial payment flow

`PaymentCalculator.calculateBalance(totalDue, paymentsApplied)` returns a `PaymentBalance` whose
`remaining = totalDue - totalPaid`: positive means underpaid, negative means overpaid, zero means
settled. `OutstandingAmount.from(balance)` clamps that to a non-negative "amount still owed" (an
overpayment is never reported as a negative debt). `PaymentCalculator.allocate(totalDue,
capturedByPayment)` reconciles multiple separate payments against one total, in the map's iteration
order — pass a `LinkedHashMap` when priority matters.

## Extension points

Every pluggable behavior is discoverable through the `fin-api` `ExtensionRegistry`
(`PaymentExtensions.registerDefaults(registry)` wires in every built-in default — no
`switch`/`instanceof` anywhere):

| Extension point | Purpose |
|---|---|
| `LifecycleProvider` | The whole state/transition table |
| `PaymentCalculator` | Balance/outstanding/settlement/allocation |
| `PaymentValidator` | Structural and business rule checks |
| `PaymentMethodProvider` / `PaymentMethodRegistry` | Enabled payment methods |
| `PaymentGateway` / `GatewayRegistry` / `GatewaySelector` | Provider integration and selection |
| `CaptureStrategy` / `AuthorizationStrategy` | Automatic vs. manual capture, full vs. partial auth |
| `IdempotencyValidator` | NEW/REPLAYED/CONFLICT determination |
| `ErrorMapping` | Translating a provider's own error shape into `FailureReason` |

## Configuration

`PaymentConfiguration` bundles lifecycle, gateway registry/selector, method registry, capture/
authorization policy, a settlement flag (`autoSettle`), session timeout, and a retry-attempt-count
placeholder (a real retry policy is a later phase's job — this is intentionally just a bound).

## How provider modules integrate

`fin-stripe` and `fin-razorpay` (see [PROVIDER_GUIDE.md](PROVIDER_GUIDE.md)) are the two shipping
implementations: each implements `PaymentGateway` (translating its SDK calls into
`GatewayRequest`/`GatewayResponse`), implements its own error-mapping class (translating provider
errors into `FailureReason`), and registers both into the shared `ExtensionRegistry` alongside a
`PaymentProvider` descriptor — proving the Gateway SPI never required a change to this module's
public API to add a real provider. `fin-spring-boot-starter` (see [SPRING.md](SPRING.md)) can wire
either automatically from configuration properties alone.

## Example

```java
ExtensionRegistry registry = ExtensionRegistries.create();
PaymentExtensions.registerDefaults(registry);

Payment payment = PaymentBuilder.newPayment()
    .amount(Money.of("49.99", usd))
    .method(MethodBuilder.newMethod().type(StandardPaymentMethodType.CARD).maskedIdentifier("**** 4242").build())
    .build();
payment.addReference(Reference.invoice(invoiceId), clock, "system");
payment.submit(clock, "system");

GatewayRequest request = new GatewayRequest(payment.requestedAmount(), payment.method(), null, idempotencyKey, null);
GatewayResponse response = gateway.authorize(request);
if (response.success()) {
    payment.authorize(payment.requestedAmount(), response.gatewayReference(), clock, "system");
}
```

## See also

[ARCHITECTURE.md](ARCHITECTURE.md) for repo-wide conventions, [PROVIDER_GUIDE.md](PROVIDER_GUIDE.md)
for Stripe/Razorpay specifics (capture modes, retries, idempotency, webhook verification),
[INTEGRATION.md](INTEGRATION.md) for how `Payment` connects to `Invoice`/`Refund`/`Ledger`, and
[SPRING.md](SPRING.md) for zero-config wiring (`PaymentAutoConfiguration`, `ProviderAutoConfiguration`).
