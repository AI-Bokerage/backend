# Gen-Fin Spring Boot Starter

`fin-spring-boot-starter` wires the whole Gen-Fin SDK into a Spring Boot application with zero
required configuration. It is a convenience layer over the already-built finance engines
(fin-money, fin-invoice, fin-payment, fin-refund, fin-reconciliation, fin-ledger, fin-pricing,
fin-dunning, fin-document, fin-provider-api) — it adds no business logic of its own and never
modifies any engine's public API.

## Installation

**Gradle:**

```kotlin
dependencies {
    implementation("io.genfin:fin-spring-boot-starter:1.0.0-alpha1")

    // Optional — add only the payment providers you use:
    implementation("io.genfin:fin-stripe:1.0.0-alpha1")
    implementation("io.genfin:fin-razorpay:1.0.0-alpha1")
}
```

**Maven:**

```xml
<dependency>
  <groupId>io.genfin</groupId>
  <artifactId>fin-spring-boot-starter</artifactId>
  <version>1.0.0-alpha1</version>
</dependency>
<!-- Optional -->
<dependency>
  <groupId>io.genfin</groupId>
  <artifactId>fin-stripe</artifactId>
  <version>1.0.0-alpha1</version>
</dependency>
```

That's it — start your application and every module's default extensions are already registered.
Inject `io.genfin.api.spi.ExtensionRegistry` (or, for fin-pricing/fin-ledger, the `PricingEngine`/
`PostingEngine` beans directly) wherever you need them.

## Zero-config quick start

```java
@Component
class InvoiceService {

  private final ExtensionRegistry registry;

  InvoiceService(ExtensionRegistry registry) {
    this.registry = registry;
  }

  void doSomething() {
    InvoiceCalculator calculator = registry.find(InvoiceCalculator.class).orElseThrow();
    // ...
  }
}
```

No `registerDefaults` calls anywhere in application code — `fin-autoconfigure` already did that
for all ten modules before your beans are constructed.

## Property reference

Every property below corresponds to a real, already-existing SDK concept — nothing here was
invented for this integration. If a module has no genuinely configurable default, it has no
properties class (adding one with no real fields would be exactly the invented-config
anti-pattern this integration avoids).

```yaml
genfin:
  document:
    renderer: json                 # default: json. Any of: json, text, markdown, csv, xml, pdf,
                                    # or a custom renderer bean's id (see below).
  invoice:
    numbering-strategy: timestamp  # default: timestamp. Also: sequential, uuid.
    numbering-sequential-template: "INV-{SEQ}"  # required only when numbering-strategy=sequential
  payment:
    default-provider: stripe       # optional. When unset, the first capable registered gateway
                                    # handles each request (fin-payment's own default behavior).
    providers:
      stripe:
        api-key: sk_live_...
        webhook-secret: whsec_...
      razorpay:
        key-id: rzp_live_...
        key-secret: ...
        webhook-secret: whsec_...
  dunning:
    enabled: true                  # default: true. Set false to skip fin-dunning wiring entirely
                                    # (this is a Spring-only gate — fin-dunning has no such field).
```

Not configurable, because no such concept exists in the SDK today: a "default currency" for
fin-money, a "rounding mode" for fin-pricing (rounding lives in fin-money's own
`RoundingStrategies`, which already has sensible zero-config defaults).

## Overriding a bean

Every autoconfigured bean is `@ConditionalOnMissingBean` — declare your own bean of the same type
and the SDK's default is skipped entirely:

```java
@Bean
CurrencyProvider currencyProvider() {
  return new MyCurrencyProvider();
}
```

This works for every module's extension points, not just the ones with dedicated Spring
bean-discovery support below — inspect `spi.XExtensions.registerDefaults` in the module you're
customizing to see exactly which port types it registers, then declare a `@Bean` of that type.

## Adding a payment provider (Stripe example, end to end)

1. Add the dependency:
   ```kotlin
   implementation("io.genfin:fin-stripe:1.0.0-alpha1")
   ```
2. Configure it:
   ```yaml
   genfin:
     payment:
       providers:
         stripe:
           api-key: sk_live_...
           webhook-secret: whsec_...
   ```
3. That's it. `ProviderAutoConfiguration` detects `StripeGateway` on the classpath
   (`@ConditionalOnClass`) and the `api-key` property (`@ConditionalOnProperty`), builds a
   `PaymentGateway` bean from it, and `PaymentAutoConfiguration` discovers that bean and adds it to
   the shared `GatewayRegistry` under provider id `"stripe"` — no manual registration anywhere.
4. To prefer Stripe over other configured providers, set `genfin.payment.default-provider: stripe`.
5. To add your own provider instead of Stripe/Razorpay, just declare a `@Bean` of type
   `PaymentGateway` yourself — the same discovery mechanism picks it up.

## Adding a custom document renderer

`fin-document`'s built-in renderer set (json/text/markdown/csv/xml/pdf) has no public extension
point of its own — this is a real gap in fin-document, not something this integration works around
inside that module. `DocumentAutoConfiguration` adds a Spring-layer-only decorator that checks
Spring-discovered `DocumentRenderer` beans by id before falling back to the built-in set:

```java
@Bean
DocumentRenderer htmlRenderer() {
  return new MyHtmlRenderer(); // id() returns RendererId.of("html")
}
```

```yaml
genfin:
  document:
    renderer: html
```

This is Spring-layer convenience only, documented here rather than as a fin-document capability.

## Custom `ExtensionRegistry` entries for modules with no dedicated Spring hook

Most modules (fin-refund, fin-reconciliation, fin-ledger's non-facade extensions, fin-dunning,
fin-pricing's non-facade extensions) have no Spring bean-discovery wired up beyond registering
their defaults — that's how the SDK already works without Spring, and it's most of what these
modules need. To override one of their extension points, register your implementation into the
shared registry **before** the owning `XAutoConfiguration` runs (registration order matters:
`ExtensionRegistry.find` returns the first-registered implementation):

```java
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)
ApplicationRunner registerCustomPostingPolicy(ExtensionRegistry registry) {
  return args -> registry.register(PostingPolicy.class, myPostingPolicy);
}
```

In practice it's simpler to just declare a `@Bean` of the extension-point type when the module
exposes one for Spring to discover (Money's `CurrencyProvider`/`ExchangeRateProvider`, Document's
`PlaceholderProvider`/`LetterheadProvider`/`DocumentRenderer`, Payment's `PaymentGateway`) — those
are already wired to register before defaults automatically.

## Injecting fin-pricing / fin-ledger facades directly

These are the only two modules with a genuine single top-level "do the work" object, so they're
exposed as ordinary beans:

```java
@Component
class PricingService {
  PricingService(PricingEngine pricingEngine) { ... }
}

@Component
class LedgerService {
  LedgerService(PostingEngine postingEngine) { ... }
}
```

## Health indicator

If your application already depends on `spring-boot-starter-actuator`, a `HealthIndicator` bean
reports the number of registered payment providers and custom document renderers. It makes no
network calls. If actuator isn't on the classpath, no bean is produced — it's entirely inert.

## Worked example

See `demo/demo-invoice` for a complete, runnable Spring Boot application: resolves
`InvoiceCalculator` from the injected `ExtensionRegistry`, registers a custom `PaymentGateway` bean
(proving automatic discovery), and renders a document via the same registry — console output only,
no REST controllers. Run it with `./gradlew :demo-invoice:bootRun`.

## See also

[ARCHITECTURE.md](ARCHITECTURE.md) for the conventions every autoconfigured module follows,
[EXTENSIONS.md](EXTENSIONS.md) for what's happening under the hood (this starter's whole job is
automating what that document describes manually), [INTEGRATION.md](INTEGRATION.md) for the
full engine-to-engine wiring guide, and [PROVIDER_GUIDE.md](PROVIDER_GUIDE.md) for Stripe/Razorpay
specifics (capture modes, retries, idempotency, webhook verification) beyond what this document
covers for Spring wiring alone.
