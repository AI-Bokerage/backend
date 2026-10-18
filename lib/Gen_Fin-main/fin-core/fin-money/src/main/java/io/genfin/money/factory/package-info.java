/**
 * Configuration-bound factories — the primary construction entry points so consumers never call
 * value-object constructors directly. {@code CurrencyFactory} lives in {@code
 * io.genfin.money.currency} and {@code MoneyFormatters}/{@code ConfigurationFactory}-equivalent
 * live in their own feature packages; this package holds the ones that need a bound {@link
 * io.genfin.money.config.MoneyConfiguration}.
 */
package io.genfin.money.factory;
