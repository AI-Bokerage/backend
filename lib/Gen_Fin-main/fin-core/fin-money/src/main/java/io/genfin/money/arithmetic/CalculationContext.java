package io.genfin.money.arithmetic;

import io.genfin.money.currency.Currency;

/** Per-call context: which currency an operation is scaled for, under which policy. */
public record CalculationContext(Currency currency, ArithmeticPolicy policy) {}
