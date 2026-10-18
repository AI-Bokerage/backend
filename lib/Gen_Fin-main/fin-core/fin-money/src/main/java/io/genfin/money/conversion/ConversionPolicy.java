package io.genfin.money.conversion;

import io.genfin.money.port.conversion.ExchangeRateProvider;

/** Bundles the rate provider a {@code CurrencyConverter} should consult. */
public record ConversionPolicy(ExchangeRateProvider rateProvider) {}
