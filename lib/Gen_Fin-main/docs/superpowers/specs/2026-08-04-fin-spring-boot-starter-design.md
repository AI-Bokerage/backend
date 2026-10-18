# Gen-Fin — Phase 14 Design: Spring Boot Starter

Date: 2026-08-04
Status: Approved for planning
Part of: Phase 14 — Spring Boot integration layer (developer experience, not business logic)

## Context

`fin-autoconfigure` and `fin-spring-boot-starter` (`spring-starter/`) exist as empty Gradle
modules — `build.gradle.kts` already wired (spring-boot-dependencies BOM, `spring-boot-autoconfigure`
on `fin-autoconfigure`, `spring-boot-starter` on `fin-spring-boot-starter`), no `src/main/java` yet.
Four demo apps (`demo-basic`, `demo-invoice`, `demo-stripe`, `demo-razorpay`) are bare
`@SpringBootApplication` stubs with dependencies already declared. This phase fills in
`fin-autoconfigure`, `fin-spring-boot-starter`, one of the demo apps, and `SPRING.md`. It does
**not** modify any existing fin-* module's public API.

This is a convenience layer, not a tenth business-logic engine. Every module already has its own
`spi.XExtensions.registerDefaults(ExtensionRegistry)` entry point (10 of them: Money, Invoice,
Payment, Refund, Reconciliation, Ledger, Pricing, Dunning, Document, plus
`ProviderApiExtensions`). The Spring layer's entire job is: build one `ExtensionRegistry`, call
each module's `registerDefaults` in the right order, let Spring-discovered beans override the
defaults, and expose whatever facades genuinely exist as injectable beans. Nothing more.

## Reality check against the original brief's example config

Research across all 10 modules (grep + read every `XExtensions` class and its port types) found
several of the brief's example properties don't correspond to any real SDK concept. Per the
brief's own "do not invent configuration for future features" rule, the actual property surface is
narrower than the example:

| Brief's example | Reality | Decision |
|---|---|---|
| `genfin.money.default-currency: INR` | No "default currency" field anywhere in fin-money. `CurrencyProvider`→`IsoCurrencyCatalog`, `ExchangeRateProvider`→`inMemoryRates()` are the only registered defaults; neither takes a "default currency." | Drop. Not invented. |
| `genfin.payment.default-provider: stripe` | `GatewaySelector` only has `firstCapable()` (picks the first registered gateway supporting the request's method) — no select-by-name concept in fin-payment itself. | Keep the property, but implement selection-by-name as a **new small class inside fin-autoconfigure** (`NamedGatewaySelector implements GatewaySelector`, falls back to `firstCapable()` when the named provider isn't registered or doesn't support the request). This is Spring-layer code implementing an existing public extension point — not a modification to fin-payment. |
| `genfin.document.renderer: pdf` | Real. `DocumentEngineConfiguration.defaultRenderer` exists, defaults to `RendererId.of("json")`. | Keep as-is. |
| `genfin.invoice.numbering: sequential` | `InvoiceExtensions` registers `InvoiceNumberGenerators.timestamp()` by default; `sequential(NumberTemplate)` exists but needs a template argument, not a bare enum value. | Keep `genfin.invoice.numbering-strategy: timestamp|sequential` (default `timestamp`, matching the SDK's own default) plus `genfin.invoice.numbering-sequential-template` (only consulted when strategy=sequential). Both map to real constructors. |
| `genfin.dunning.enabled: true` | No `enabled` field anywhere in fin-dunning. | Keep as a Spring-only `@ConditionalOnProperty(prefix = "genfin.dunning", name = "enabled", matchIfMissing = true)` gate on the whole `DunningAutoConfiguration` class — a Spring convention, not an invented SDK field. |
| `genfin.pricing.rounding-mode: HALF_UP` | No `RoundingMode` concept in fin-pricing at all — rounding lives in fin-money (`RoundingStrategies`/`PrecisionPolicies`). | Drop from pricing. Not added to money either — fin-money's rounding defaults are already sensible zero-config; no evidence any consumer needs to override them yet, and adding the property without a proven need is exactly the over-engineering the brief warns against. |

Every other module's `@ConfigurationProperties` surface is limited to values that already have a
real corresponding field/constructor — enumerated per-module in the plan.

## No single "engine bean per module" — corrected mental model

Contrary to the brief's implicit assumption, most modules don't have one aggregate "engine" object
to expose as a bean. Every module exposes many independent extension points
(`XExtensions.registerDefaults` registers 5-20 separate port types per module), each re-derived via
its own `X.from(registry)`/`XFactory.create(registry, ...)` call. Only two modules have a genuine
single top-level "do the work" facade:

- **fin-pricing**: `PricingEngine` (`PricingEngines.from(registry)` / `.of(pipeline)`)
- **fin-ledger**: `PostingEngine` (`PostingEngines.from(registry)`)

Design decision: `PricingAutoConfiguration` and `LedgerAutoConfiguration` each expose their one real
facade as a `@Bean`. Every other `XAutoConfiguration` class's job is simply: **populate the shared
`ExtensionRegistry`** with that module's defaults (after any Spring-discovered overrides), matching
what applications already do manually today. `SPRING.md` documents, per module, which extension
points exist and how to pull a working object via `X.from(registry)` for modules with no facade —
this is not a gap, it's how the SDK already works; Spring's contribution is skipping the manual
`ExtensionRegistries.create()` + ten `registerDefaults` calls.

## Architecture

### `GenFinExtensionRegistryAutoConfiguration` (fin-autoconfigure, one class, loads first)

Exposes the single shared bean:

```java
@Bean
@ConditionalOnMissingBean
ExtensionRegistry extensionRegistry() {
  return ExtensionRegistries.create();
}
```

Every module `XAutoConfiguration` below is `@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)`
and takes `ExtensionRegistry` as a constructor/method parameter.

### Per-module `XAutoConfiguration` classes (Group 1)

Ten classes: `MoneyAutoConfiguration`, `InvoiceAutoConfiguration`, `PaymentAutoConfiguration`,
`RefundAutoConfiguration`, `ReconciliationAutoConfiguration`, `LedgerAutoConfiguration`,
`PricingAutoConfiguration`, `DunningAutoConfiguration`, `DocumentAutoConfiguration`,
`ProviderAutoConfiguration`. Each is a `@AutoConfiguration` (Spring Boot 3's replacement for plain
`@Configuration` + `spring.factories`), registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

**Common shape** (most modules — Refund, Reconciliation, Ledger\*, Pricing\*, Dunning, Invoice):

```java
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
public class RefundAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "refundExtensionsRegistered")
  Boolean refundExtensionsRegistered(ExtensionRegistry registry) {
    RefundExtensions.registerDefaults(registry);
    return Boolean.TRUE;
  }
}
```

(\*Ledger/Pricing additionally expose their real facade bean, built after the marker bean via
`@DependsOn`.)

**Modules with genuine Spring bean-discovery hooks** (Group 4) additionally discover and register
application-supplied implementations **before** calling `registerDefaults`, since
`ExtensionRegistry.find` returns the first-registered implementation for a type (confirmed in
`DefaultExtensionRegistry`) — this is the existing SDK convention already documented in
`fin-document`'s `DOCUMENT.md`, not a new rule:

- **MoneyAutoConfiguration**: discovers `ObjectProvider<List<CurrencyProvider>>` and
  `ObjectProvider<List<ExchangeRateProvider>>`, registers each found bean, then calls
  `MoneyExtensions.registerDefaults(registry)`.
- **DocumentAutoConfiguration**: discovers `List<PlaceholderProvider>` and `List<LetterheadProvider>`
  Spring beans and feeds them into `PlaceholderResolvers.standard()`/`LetterheadResolvers.standard()`
  `Bundle.register(...)` (the module's real extension mechanism for these two — they're not raw
  `ExtensionRegistry` entries, they're fed into a `Bundle` whose `.resolver()` is what actually gets
  registered under `PlaceholderResolver`/`LetterheadResolver`).
  **Renderer extensibility gap (flagging, not silently working around):** `DocumentRenderers.standard()`
  has no `Bundle`/`register()` — it's a closed set of 6 built-ins with no public extension point in
  `fin-document` today (confirmed: `DefaultRendererRegistry`/`DefaultRendererResolver` are internal,
  not exported). A Spring-discovered custom `DocumentRenderer` bean therefore cannot be wired into
  `RendererResolver` without either modifying fin-document (out of scope) or a Spring-layer
  decorator. This phase adds a small `SpringAwareRendererResolver implements RendererResolver`
  (in `fin-autoconfigure` only) that checks Spring-discovered `DocumentRenderer` beans by id first,
  falling back to `DocumentRenderers.standard()` — documented in `SPRING.md` as the supported way to
  add a custom renderer, with an explicit note that this is a Spring-layer convenience, not a
  fin-document capability.
- **PaymentAutoConfiguration**: exposes a `GatewayRegistry` bean (`GatewayRegistries.empty()`),
  discovers `ObjectProvider<List<PaymentGateway>>`, validates no two discovered gateways share a
  `provider().providerId()` (Group 7 — fail fast with a clear message; `DefaultGatewayRegistry.register`
  silently overwrites on collision, so this check must happen at the Spring layer), registers each
  by its `providerId()`, then registers `GatewaySelector` — either `NamedGatewaySelector` (new class,
  wraps `firstCapable()`, prefers `genfin.payment.default-provider` when set and capable) if the
  property is set, else `GatewayRegistries.firstCapable()` directly — then calls
  `PaymentExtensions.registerDefaults(registry)`.
- **ProviderAutoConfiguration**: calls `ProviderApiExtensions.registerDefaults(registry)`.
  `@ConditionalOnClass(name = "io.genfin.stripe.gateway.StripeGateway")` +
  `@ConditionalOnProperty(prefix = "genfin.payment.providers.stripe", name = "api-key")` builds a
  `StripeConfiguration` from `@ConfigurationProperties("genfin.payment.providers.stripe")` and
  exposes `new StripeGateway(configuration)` as a `PaymentGateway` bean (same treatment for
  Razorpay, `@ConditionalOnClass("io.genfin.razorpay.gateway.RazorpayGateway")`). These beans are
  then picked up by `PaymentAutoConfiguration`'s `List<PaymentGateway>` discovery above — this is
  the concrete mechanism behind "if a user adds fin-stripe, Spring discovers it automatically."
  Neither fin-stripe nor fin-razorpay gains any Spring dependency; the bean-construction code lives
  entirely in `fin-autoconfigure`, conditional on the provider jar being present on the classpath.

### `@ConfigurationProperties(prefix = "genfin")` (Group 3)

One root properties class per module with only real fields, e.g.:

```java
@ConfigurationProperties(prefix = "genfin.document")
public class DocumentProperties {
  private String renderer = "json"; // mirrors DocumentEngineConfiguration's own default
  // getters/setters
}

@ConfigurationProperties(prefix = "genfin.payment")
public class PaymentProperties {
  private String defaultProvider; // nullable — no default; firstCapable() used when unset
}

@ConfigurationProperties(prefix = "genfin.invoice")
public class InvoiceProperties {
  private String numberingStrategy = "timestamp";
  private String numberingSequentialTemplate;
}

@ConfigurationProperties(prefix = "genfin.dunning")
public class DunningProperties {
  private boolean enabled = true; // gates the AutoConfiguration only, not an SDK field
}
```

Registered via `@EnableConfigurationProperties` on each owning `XAutoConfiguration`. Modules with
no real configurable default (Refund, Reconciliation, Ledger, Pricing, Money beyond what's listed
above) get no properties class — an empty `@ConfigurationProperties` class with no fields is exactly
the invented-config anti-pattern the brief warns against.

### `fin-spring-boot-starter` (Group 6)

Unchanged from its current `build.gradle.kts` shape — `api(project(":fin-autoconfigure"))` plus
`api("org.springframework.boot:spring-boot-starter")`. This phase adds nothing to this module
beyond what's already declared; the starter's only job is dependency aggregation (already done) —
confirming there's no `src/main/java` needed here at all beyond, if Spring Boot's tooling requires
it, an empty marker. Stripe/Razorpay remain separate optional dependencies, exactly as scaffolded.

### `GenFinHealthIndicator` (Group 8, minimal)

`@ConditionalOnClass(HealthIndicator.class)` (so it's inert without Actuator on the classpath — no
new dependency added to `fin-autoconfigure`, purely conditional on the consuming app already having
`spring-boot-starter-actuator`). Reports which of the 10 modules' extension marker beans are present
in the context, how many `PaymentGateway`/`DocumentRenderer` beans are registered, by reading the
`ExtensionRegistry` and the discovered bean lists — no network calls, matches the brief's explicit
"do not ping external APIs."

### Bean validation (Group 7)

Fail-fast checks, each thrown as a plain `IllegalStateException` during autoconfiguration (Spring's
own startup-failure reporting handles the rest — no custom exception hierarchy):

- Duplicate `PaymentGateway.provider().providerId()` among discovered beans (PaymentAutoConfiguration).
- `genfin.payment.default-provider` set to an id that matches no discovered gateway — logged as a
  warning, not fatal (the selector already falls back to `firstCapable()`), since a provider jar
  might load after this check in edge orderings — documented as a WARN, not a startup failure, to
  avoid brittleness.
- `genfin.document.renderer` set to an id `DocumentRenderers.standard()` doesn't know and no
  Spring-discovered `DocumentRenderer` bean provides either — fatal, since every reachable renderer
  id is known at context-refresh time.

## Package layout

```
spring-starter/fin-autoconfigure/src/main/java/io/genfin/autoconfigure/
├── GenFinExtensionRegistryAutoConfiguration.java
├── money/MoneyAutoConfiguration.java
├── invoice/InvoiceAutoConfiguration.java, InvoiceProperties.java
├── payment/PaymentAutoConfiguration.java, PaymentProperties.java, NamedGatewaySelector.java
├── refund/RefundAutoConfiguration.java
├── reconciliation/ReconciliationAutoConfiguration.java
├── ledger/LedgerAutoConfiguration.java
├── pricing/PricingAutoConfiguration.java
├── dunning/DunningAutoConfiguration.java, DunningProperties.java
├── document/DocumentAutoConfiguration.java, DocumentProperties.java, SpringAwareRendererResolver.java
├── provider/ProviderAutoConfiguration.java, StripeProperties.java, RazorpayProperties.java
└── health/GenFinHealthIndicator.java
src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

No `port`/`internal` split here — this module has no extension points of its own to protect; it's
entirely `@Configuration`-shaped glue. `module-info.java`: none — Spring Boot autoconfiguration
modules conventionally run on the classpath, not the module path (matches `spring-boot-autoconfigure`
itself, which ships no `module-info.java`); confirmed no other fin-* Spring module exists yet to
contradict this, and `fin-autoconfigure`'s `build.gradle.kts` already uses the plain
`java-library-conventions` plugin shared with module-path modules, but Spring Boot's classpath
scanning (`@ConditionalOnClass`, `spring.factories`-equivalent) does not work reliably under the
module path — omitting `module-info.java` here is the correct, conventional choice, not a shortcut.

## Testing (Group 11)

- **Context loading test**: `@SpringBootTest` with a minimal test app, asserts the context loads
  with `fin-spring-boot-starter` alone (no Stripe/Razorpay on the classpath).
- **Per-`XAutoConfiguration` tests**: `ApplicationContextRunner` — assert defaults load, assert a
  user-supplied `@Bean` of the relevant type suppresses the default (`@ConditionalOnMissingBean`
  proof), one test per module (10 total, each ~15 lines).
- **`@ConfigurationProperties` binding tests**: for Document/Invoice/Payment/Dunning properties —
  assert YAML values bind to the right fields.
- **Provider discovery test**: register two `PaymentGateway` test doubles with different
  `providerId()`s, assert both resolvable via `GatewayRegistry`; register two with the *same* id,
  assert the fail-fast `IllegalStateException`.
- **Starter integration test**: in `integration-tests` module or a dedicated test source set,
  assert `fin-spring-boot-starter` alone (no explicit `fin-autoconfigure` dependency) produces a
  working context — proves the aggregation actually works, not just that the pieces compile.
- Full `./gradlew build` green is the stage-level gate (per standing instruction: no per-task
  builds, one verification pass at the end of this phase).

## `SPRING.md` (Group 9)

Location: repo root (mirrors `README.md`, not module-root — this document is about integrating the
whole SDK via Spring, not one engine module). Sections: installation (Gradle + Maven), zero-config
quick start, full property reference (only real properties, from the corrected table above),
overriding a bean (`@ConditionalOnMissingBean` example), adding a payment provider (Stripe example
end to end: dependency + properties + automatic discovery), adding a custom document renderer (via
`SpringAwareRendererResolver`, with the documented caveat that this is a Spring-layer feature),
using a custom `ExtensionRegistry` entry for a module with no dedicated Spring hook (generic
`registry.register(X.class, myImpl)` pattern, called from an `ApplicationRunner` or a
`@PostConstruct` after autoconfiguration — since ordering matters, document it precisely), a
worked example per fin-pricing/fin-ledger facade injection.

## Demo (Group 10)

Fills in `demo/demo-invoice`'s existing empty `InvoiceDemoApplication.java` (already depends on
`fin-spring-boot-starter` + `fin-invoice` per its `build.gradle.kts`) — a `CommandLineRunner` that:
builds an `Invoice` via the injected `ExtensionRegistry` (`InvoiceCalculators.from(registry)` or
equivalent), registers a trivial custom `PaymentGateway` bean in the same app to prove discovery,
and renders a receipt via the injected `DocumentAutoConfiguration`-provided renderer path. No REST
controllers — just console output via `CommandLineRunner`, matching the brief's "no REST APIs, just
a runnable example." `demo-basic`/`demo-stripe`/`demo-razorpay` are left as their current bare
stubs — the brief asks for "one small example," not four.

## Out of Scope (restated from the brief, plus this design's own additions)

Observability/Metrics/Micrometer/OpenTelemetry/tracing, custom Actuator endpoints beyond the one
health indicator, GraalVM/native hints, cloud/Kubernetes integrations, auto-retries, any custom DI
framework. Additionally, per this design's research: `genfin.money.default-currency` and
`genfin.pricing.rounding-mode` (no corresponding SDK concept — see reality-check table), and any
attempt to make `fin-document`'s renderer set genuinely pluggable inside fin-document itself (the
`SpringAwareRendererResolver` workaround stays Spring-layer-only; fixing the underlying gap in
fin-document, if ever needed, is separate follow-up work on that module, not this phase).
