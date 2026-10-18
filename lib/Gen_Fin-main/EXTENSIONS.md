# Extension Points and the `ExtensionRegistry`

Every pluggable behavior across every Gen-Fin module is discoverable through one shared type:
`io.genfin.api.spi.ExtensionRegistry` (in `fin-api`). This document is the plain-Java (non-Spring)
guide to using it. If your application is a Spring Boot service, you almost never need any of
this directly — see [SPRING.md](SPRING.md), where it's automatic.

## The registry contract

```java
public interface ExtensionRegistry {
  <T> void register(Class<T> extensionPoint, T implementation);
  <T> Optional<T> find(Class<T> extensionPoint);
  <T> List<T> findAll(Class<T> extensionPoint);
}
```

- `register` **accumulates** — registering a second implementation under the same type doesn't
  overwrite the first, it adds to it.
- `find` returns the **first-registered** implementation for a type.
- `findAll` returns every registered implementation, in registration order — used by extension
  points that legitimately support multiple simultaneous implementations (e.g. multiple
  `PaymentGateway`s, multiple `DocumentRenderer`s).
- The default implementation, `DefaultExtensionRegistry`, is backed by a `ConcurrentHashMap` —
  safe for concurrent registration and lookup.

## Bootstrapping: one registry, ten `registerDefaults` calls

```java
ExtensionRegistry registry = ExtensionRegistries.create();

MoneyExtensions.registerDefaults(registry);
InvoiceExtensions.registerDefaults(registry);
PaymentExtensions.registerDefaults(registry);
RefundExtensions.registerDefaults(registry);
ReconciliationExtensions.registerDefaults(registry);
LedgerExtensions.registerDefaults(registry);
PricingExtensions.registerDefaults(registry);
DunningExtensions.registerDefaults(registry);
DocumentExtensions.registerDefaults(registry);
ProviderApiExtensions.registerDefaults(registry);
```

Order among these ten calls doesn't matter — none of them look up another module's extension
points during registration. Do this once, at application startup, and keep the `registry`
instance alive for the application's lifetime.

## Overriding a default

Because `find()` returns the first-registered implementation, **register your override before
calling the owning module's `registerDefaults`**:

```java
ExtensionRegistry registry = ExtensionRegistries.create();

registry.register(CurrencyProvider.class, myCurrencyProvider); // registered first — wins
MoneyExtensions.registerDefaults(registry);                    // its own CurrencyProvider loses
```

This ordering requirement is the single most important thing to know about the registry — get it
backwards and your override silently loses to the built-in default.

## Extension points ship empty, by design, in several modules

`fin-ledger`'s `AccountTypeRegistry`/`PostingRuleRegistry`, `fin-pricing`'s `CatalogRegistry`, and
`fin-dunning`'s `PolicyRegistry` are registered **empty** by their own `registerDefaults` — Gen-Fin
ships no built-in chart of accounts, posting rules, product catalog, or dunning policy. These
engines do nothing useful until a consuming application registers its own real business rules.
This is intentional (see each module's own `*_ENGINE.md`), not a gap to work around.

## Where to find a module's full extension-point list

Each module's `spi.XExtensions.registerDefaults(ExtensionRegistry)` method is the definitive,
always-up-to-date list — read that one method rather than a table here that could drift. The
per-module `*_ENGINE.md` docs each include an "at a glance" table of the highlights, with a link
to the module's own deeper doc for the full picture.

## See also

[ARCHITECTURE.md](ARCHITECTURE.md) for the layering these extension points live inside,
[INTEGRATION.md](INTEGRATION.md) for how they chain across modules in a real integration, and
[SPRING.md](SPRING.md) for automatic registration in a Spring Boot application.
