# Money Engine (`fin-money`)

The mathematical foundation of Gen-Fin. Pure Java, zero framework/persistence/provider
dependencies. Every finance module operates on `Money` — never on raw `BigDecimal`.

## Package map

```
io.genfin.money
├── money/          Money — the immutable value object everything revolves around
├── currency/       Currency value object + CurrencyFactory
├── arithmetic/     ArithmeticPolicy, CalculationContext, ArithmeticPolicies
├── precision/      PrecisionPolicy, ScalePolicy, PrecisionPolicies
├── rounding/       RoundingStrategy + RoundingStrategies (HALF_UP/HALF_EVEN/DOWN/CEILING/FLOOR/custom)
├── conversion/     ExchangeRate, ConversionContext/Policy, CurrencyConverters
├── allocation/     AllocationStrategies (equal/ratio/percentage/weighted — one lossless engine)
├── percentage/     Percentage, Discount/Markup/Margin/CompoundPercentage calculators
├── tax/            TaxContext/Metadata/Breakdown/Component/Category — extension points, zero tax logic
├── format/         MoneyFormatter/Parser, FormattingContext/Policy
├── serialization/  Money/Currency/TaxMetadata Codecs (fin-api contracts, no Jackson)
├── config/         MoneyConfiguration (immutable) + MoneyConfigurations (ConfigurationFactory)
├── factory/        MoneyFactory, CalculatorFactory (config-bound construction)
├── spi/            MoneyExtensions — bootstraps defaults into a fin-api ExtensionRegistry
├── exception/      MoneyErrorCode + exception hierarchy (built on fin-api's GenFinException)
└── port/<feature>/ The pluggable side of every feature above (interfaces only)
    internal/<feature>/ Hidden default implementations (never exported by module-info)
```

## Money lifecycle

1. **Create** — `Money.of(amount, currency)` (standard policy) or `Money.of(amount, currency, policy)`
   (explicit `ArithmeticPolicy`), or via a config-bound `MoneyFactory`. The constructor is private;
   construction always normalizes the amount to the currency's scale immediately.
2. **Operate** — `add`/`subtract` require the same currency (throw `CurrencyMismatchException`
   otherwise) unless a `CurrencyConverter` + `ConversionContext` are explicitly supplied.
   `multiply`/`divide` delegate to a `MoneyCalculator`, never compute inline.
3. **Compare / inspect** — `compareTo`, `min`, `max`, `isZero`/`isPositive`/`isNegative`.
4. **Leave the engine** — via a `MoneyFormatter` (for display) or a `MoneyCodec` (for storage/transport).

## Currency lifecycle

`Currency` is independent of `java.util.Currency` (the JDK type is touched in exactly one place,
`IsoCurrencyCatalog`, to seed ISO-4217 data — it never appears in a public signature). Currencies
are built via `CurrencyFactory`, held in a `CurrencyRegistry`, and discovered — never hardcoded —
by any module that needs one. `CurrencyRegistries.iso()` gives a pre-seeded registry;
`CurrencyRegistries.empty()` starts blank for custom/crypto/enterprise currencies.

## Calculation flow

`Money` never contains arithmetic. Every operation resolves a `MoneyCalculator`
(`CalculatorFactory.forCurrency(currency)` by default, or an explicit one you pass in), which
consults an `ArithmeticPolicy` — the bundle of `ScalePolicy` (scale per currency), `PrecisionPolicy`
(`MathContext`/`RoundingMode` for the raw calculation), `RoundingStrategy` (final scale rounding),
and `OverflowPolicy` (magnitude guard). Swap any one policy without touching `Money` or the others.

## Extension points

Everything that varies by deployment or jurisdiction is a `port.*` interface, discoverable through
the `fin-api` `ExtensionRegistry` (`MoneyExtensions.registerDefaults(registry)` wires in every
built-in default). No `switch`/`instanceof` chains gate behavior anywhere in this module:

| Extension point | Purpose |
|---|---|
| `CurrencyProvider` | Bulk currency sourcing (ISO, crypto, enterprise ledgers) |
| `ExchangeRateProvider` / `CurrencyConverter` | Rate lookup and conversion |
| `AllocationStrategy` | Splitting a total into shares without losing cents |
| `MoneyFormatter` / `MoneyParser` / `LocaleResolver` | Display and parsing |
| `TaxCalculator` / `TaxStrategy` / `TaxPolicy` / `TaxRulesProvider<R>` | Tax computation (Phase 4) |

## Tax extension model

This module is **tax-ready but tax-free**: `TaxContext → TaxableAmount → TaxCategory`,
`TaxComponent → TaxBreakdown → TaxComputationResult` model the shape of a tax calculation with zero
jurisdiction logic — no GST, VAT, or Sales Tax rates or formulas. The only implementation provided
is `TaxCalculators.noOp()` (always zero tax), a legitimate default for tax-disabled contexts, not a
tax rule. Phase 4 implements `TaxCalculator`/`TaxStrategy`/`TaxRulesProvider<R>` for each
jurisdiction and registers them through the same `ExtensionRegistry` — no change to any type in
this document is required.

## Example

```java
Currency usd = CurrencyFactory.newCurrency()
    .code("USD").symbol("$").displayName("US Dollar").fractionDigits(2).build();

Money price = Money.of("199.99", usd);
Money discounted = DiscountCalculator.apply(price, Percentage.ofPercent(new BigDecimal("15")));
List<Money> installments = AllocationStrategies.equal(discounted, 3); // sums back to `discounted` exactly

String display = MoneyFormatters.standard()
    .format(discounted, FormattingContext.of(Locale.US, FormatStyle.SYMBOL_FIRST));
```

## See also

[ARCHITECTURE.md](ARCHITECTURE.md) for the repo-wide conventions this module follows,
[EXTENSIONS.md](EXTENSIONS.md) for how `ExtensionRegistry` wiring works generally,
[INTEGRATION.md](INTEGRATION.md) for how `Money` flows into `fin-invoice`/`fin-payment`, and
[SPRING.md](SPRING.md) for zero-config Spring Boot wiring (`MoneyAutoConfiguration`).
