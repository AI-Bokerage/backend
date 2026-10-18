# Provider Platform (`fin-provider-api`, `fin-stripe`, `fin-razorpay`)

A provider-neutral infrastructure layer for plugging real payment gateways into the
`fin-payment` engine — not "a Stripe module and a Razorpay module," but one reusable
platform (`fin-provider-api`) that any provider implements against, plus two reference
implementations (`fin-stripe`, `fin-razorpay`) that prove the platform is sufficient without
special-casing either one.

`fin-payment`'s public API is unchanged by this phase. Nothing here modifies
`PaymentGateway`, `GatewayRequest`, `GatewayResponse`, or any other Gateway SPI type —
`fin-provider-api` only adds infrastructure *around* that existing contract (discovery,
lifecycle, resilience, webhooks) and depends on `fin-payment`, never the reverse.

## Package map (`fin-provider-api`)

```
io.genfin.providerapi
├── descriptor/        ProviderDescriptor, ProviderId/Name/Version/Region/Priority/Environment/Status
├── capability/         ProviderCapability enum + CapabilitySet
├── auth/               AuthenticationScheme + Credential (api key / bearer / basic / HMAC / mTLS)
├── webhook/            WebhookPayload/Signature/Event value objects
├── retry/              RetryClassification/RetryDecision + BackoffStrategy (fixed/exponential)
├── health/             HealthStatus/LatencySnapshot/AvailabilitySnapshot/ProviderHealth
├── circuit/            CircuitState/CircuitMetrics + CircuitBreaker
├── ratelimit/          RateLimit/RateLimitPolicy/ThrottleDecision + token-bucket implementation
├── tokenization/       PaymentToken (Vault/Customer/Card/Network) + TokenizationProvider SPI
├── paymentlink/        PaymentLink/PaymentLinkStatus + PaymentLinkProvider SPI
├── checkout/           CheckoutMode/CheckoutSession + CheckoutProvider SPI
├── event/              ProviderRegistered/Unavailable/Recovered, WebhookProcessed, PaymentSynchronized
├── config/             ProviderConfiguration (descriptor + credential + timeout + retry + capabilities)
├── exception/          ProviderApiErrorCode + InvalidWebhookSignatureException/NoUsableProviderException/CircuitOpenException
├── spi/                ProviderApiExtensions — bootstraps every default into a fin-api ExtensionRegistry
└── port/<feature>/     The pluggable side of every feature above (interfaces only)
    internal/<feature>/ Hidden default implementations (never exported)
```

`ProviderDescriptor` reuses `io.genfin.payment.metadata.PaymentMetadata` directly rather than
inventing a second metadata type, since this module already depends on `fin-payment`.

## How a provider plugs in

A provider module needs exactly four things, all implemented against interfaces that already
exist — no changes to `fin-provider-api` or `fin-payment` required:

1. **A `PaymentGateway` implementation** (from `fin-payment`) — translates its own SDK calls
   into `GatewayRequest`/`GatewayResponse`. This is the only class the payment engine ever
   calls directly.
2. **An `ErrorMapping` implementation** (from `fin-payment`) — translates the provider's own
   exception/error shape into a generic `FailureReason`. No provider exception type crosses
   this boundary.
3. **A `WebhookVerifier` / `AuthenticationProvider` implementation** (from `fin-provider-api`)
   — signature verification and credential shape, kept separate from the gateway itself.
4. **A `ProviderDescriptor` + `ProviderFactory`**, registered into the shared
   `ProviderRegistry`/`ExtensionRegistry` via a `<Provider>Extensions.registerDefaults(registry)`
   static bootstrap class, following the same pattern used by every prior phase.

Adding a fifth provider (PayPal, Adyen, Cashfree, PhonePe, PayU, Square, Braintree, an internal
banking API) means writing a new module that does these four things — `fin-provider-api` and
`fin-payment` are read, never edited.

## The `gatewayReference` convention

`GatewayRequest` is immutable and has no field for "the provider resource to act on next" —
`authorize()` creates something; `capture()`/`voidAuthorization()`/`refund()` need to reference
what it created. Both reference gateways use the same convention: `authorize()` returns a
`GatewayResponse` whose id is echoed back by the caller as the `"gatewayReference"` metadata
key on the next `GatewayRequest`. `StripeGateway` and `RazorpayGateway` both read
`request.metadata().find("gatewayReference")` this way, keeping this detail out of the shared
SPI entirely — it's a calling convention on top of existing types, not a new contract.

Note the semantic difference this convention has to absorb: for Stripe, `gatewayReference` is
always the same `PaymentIntent` id across the whole lifecycle. For Razorpay, `authorize()`
returns an *Order* id, but `capture()`/`refund()` need a *Payment* id — a different resource,
obtained externally via Razorpay's client-side checkout callback or a webhook, not from
`authorize()`'s own response. This is documented on `RazorpayGateway` itself; it reflects a real
difference between the two platforms' architectures (Stripe's PaymentIntent is a single mutable
resource; Razorpay separates the Order a customer pays against from the Payment that pays it),
not a platform limitation.

## Reference implementations

### `fin-stripe`

Wraps the official `com.stripe:stripe-java` SDK. `authorize` calls
`PaymentIntent.create(..., CaptureMethod.MANUAL)`; `capture`/`voidAuthorization`/`refund`
retrieve the PaymentIntent by `gatewayReference` and call `.capture()`/`.cancel()`, or create a
`Refund`. Webhook verification (`StripeWebhookVerifier`) reimplements Stripe's real signature
scheme from scratch with plain JDK crypto (`javax.crypto.Mac`, constant-time compare) rather than
delegating to the SDK's own header-parsing utility, so it's independently testable: it parses the
`t=<timestamp>,v1=<hex>` `Stripe-Signature` header, then verifies HMAC-SHA256 over
`"<timestamp>.<rawBody>"` — pass the raw header value as `WebhookSignature.value()`, not a bare
hex digest.

### `fin-razorpay`

Wraps the official `com.razorpay:razorpay-java` SDK. `authorize` creates an Order (the closest
analog to an unconfirmed PaymentIntent — Razorpay's checkout flow, not a server-side API call,
is what actually collects payment against it). `voidAuthorization` always returns failure:
Razorpay has no API to void an unpaid order or payment, so `capabilities()` honestly reports
`supportsVoid = false` rather than the gateway pretending to support it. Webhook verification
delegates directly to the SDK's own `Utils.verifyWebhookSignature`, since its contract already
matches the generic shape exactly.

Both provider modules are deliberately **non-modular** (no `module-info.java`) — their SDKs
pull in non-modularized transitive dependencies (okhttp, org.json, commons-codec/text for
Razorpay), which makes a strict JPMS `requires` graph fragile. An unnamed/classpath module can
read every named module automatically, so this trades nothing at the API boundary; it matches
the existing repo precedent set by the Spring Boot starter and demo modules.

## Production readiness (release-audit findings)

Both `fin-stripe` and `fin-razorpay` are real, complete implementations, verified as of the V1
release audit — configuration, capture modes, retries, error mapping, and refunds are all fully
implemented (not stubs) for both. Two provider-specific facts worth knowing before going live:

- **Stripe idempotency**: `GatewayRequest.idempotencyKey()` reaches Stripe's own idempotency-key
  mechanism on every call — safe to retry.
- **Razorpay idempotency — known limitation**: `RazorpayGateway` deliberately does **not** forward
  the idempotency key. The `razorpay-java` SDK (1.4.10) exposes idempotency headers as a static,
  process-wide map (`RazorpayClient.addHeaders`), so setting one per-request would leak across
  concurrent requests on other threads — judged unsafe rather than silently wrong. A retried
  request against Razorpay after a transient network failure can create a duplicate order/payment;
  a FIN-SVC-side idempotency guard (e.g. keyed on your own request id, checked before calling the
  gateway) is required if this matters for your flow. Stripe has no equivalent gap.

## Compliance testing without live credentials

`fin-provider-api` publishes a shared abstract test, `PaymentGatewayComplianceTest`, via
`java-test-fixtures` (`testImplementation(testFixtures(project(":fin-provider-api")))`). Both
`StripeGatewayComplianceTest` and `RazorpayGatewayComplianceTest` extend it and supply their own
`gateway()` — the same test methods run unmodified against both implementations, proving
behavioral consistency at the level that's actually testable offline: `capabilities()` is never
null and declares at least one supported payment method, `provider()` returns a descriptor with
non-blank identity, `ProviderCapabilities` declare at least one supported currency and country,
and `health()` reports a status without throwing.

`authorize`/`capture`/`refund` need a real sandbox account and are explicitly out of scope for
this suite (documented in its Javadoc) — those code paths are covered instead by
provider-specific, network-free unit tests against the request mapper, response mapper, and
error mapping directly (e.g. `StripeRequestMapperTest` asserts a $19.99 charge maps to `1999L`
minor units and a lowercase `"usd"` currency code; `RazorpayErrorMappingTest` constructs real
`RazorpayException` instances via its public constructors to verify status-code-to-category
translation).

## Extension points

| Extension point | Purpose |
|---|---|
| `ProviderRegistry` / `ProviderSelector` / `ProviderResolver` | Discovery and selection among registered providers |
| `ProviderFactory` / `ProviderLoader` | Constructing and wiring a provider's gateway |
| `AuthenticationProvider` | Producing a `Credential` for a provider |
| `WebhookVerifier` / `WebhookParser` / `WebhookProcessor` / `WebhookReplayProtection` | The webhook pipeline, composed by `WebhookPipeline` |
| `RetryPolicy` / `BackoffStrategy` | Retry classification and backoff timing |
| `CircuitBreaker` / `CircuitBreakerFactory` | Per-provider circuit breaking |
| `RateLimitBucket` | Per-provider throttling |
| `ProviderIdempotencyMapper` | Mapping engine idempotency keys onto provider-specific ones |
| `TokenizationProvider` / `PaymentLinkProvider` / `CheckoutProvider` | Optional capability-gated features |

All are discoverable through `fin-api`'s `ExtensionRegistry` — `ProviderApiExtensions.registerDefaults(registry)`
wires in the built-in defaults; no `switch`/`instanceof` anywhere in the dispatch path.

## Example — registering Stripe as a `PaymentGateway`

```java
ExtensionRegistry registry = ExtensionRegistries.create();
PaymentExtensions.registerDefaults(registry);
ProviderApiExtensions.registerDefaults(registry);

ProviderConfiguration base = ProviderConfiguration.builder()
    .descriptor(new ProviderDescriptor(
        ProviderId.of("stripe"), new ProviderName("Stripe"), new ProviderVersion("1.0"),
        ProviderRegion.GLOBAL, ProviderEnvironment.PRODUCTION, ProviderPriority.DEFAULT,
        ProviderStatus.ACTIVE, null))
    .credential(Credential.apiKey(System.getenv("STRIPE_API_KEY")))
    .build();
StripeConfiguration stripeConfig = StripeConfiguration.of(base, System.getenv("STRIPE_WEBHOOK_SECRET"));
PaymentGateway stripe = new StripeGateway(stripeConfig);

GatewayRegistry gateways = GatewayRegistries.empty();
gateways.register(stripe.provider().providerId(), stripe);
registry.register(GatewaySelector.class, GatewayRegistries.firstCapable());
```

In a Spring Boot application, all of the above is unnecessary — see [SPRING.md](SPRING.md):
`ProviderAutoConfiguration` builds and registers the gateway from `genfin.payment.providers.stripe.*`
properties alone.

## Architecture rules enforced

An ArchUnit suite (`ProviderApiArchitectureTest`) enforces the boundaries this document
describes structurally, not just by convention:

- `port` packages must never depend on `internal` packages.
- No provider-specific name may leak into the public model — a regex bans class names ending
  in `Stripe`, `Razorpay`, `PayPal`, `Adyen`, `Cashfree`, `PhonePe`, `PayU`, `Http`, or `Sdk`
  anywhere under `io.genfin.providerapi` outside the provider modules themselves.
- Feature packages are free of cycles (scoped to exclude `internal`/`port`, which legitimately
  reference each other in both directions as part of the factory pattern).

## See also

[PAYMENT_ENGINE.md](PAYMENT_ENGINE.md) for the `PaymentGateway` SPI this platform implements
against, [SPRING.md](SPRING.md) for zero-config provider wiring, and
[ARCHITECTURE.md](ARCHITECTURE.md) for repo-wide conventions.
