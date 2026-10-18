package io.genfin.money.money;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.arithmetic.ArithmeticPolicies;
import io.genfin.money.arithmetic.ArithmeticPolicy;
import io.genfin.money.conversion.ConversionContext;
import io.genfin.money.currency.Currency;
import io.genfin.money.exception.CurrencyMismatchException;
import io.genfin.money.internal.arithmetic.DefaultMoneyCalculator;
import io.genfin.money.port.arithmetic.MoneyCalculator;
import io.genfin.money.port.conversion.CurrencyConverter;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * An immutable amount of a given {@link Currency}. Every arithmetic operation delegates to a {@link
 * MoneyCalculator} — Money itself performs no rounding or precision decisions. Operations across
 * mismatched currencies throw {@link CurrencyMismatchException} unless a conversion is explicitly
 * supplied (see {@code io.genfin.money.conversion}).
 *
 * <p>Not {@code java.io.Serializable}: {@link ArithmeticPolicy} may carry non-serializable lambda
 * strategies. Use {@code io.genfin.money.serialization.MoneyCodec} to serialize a Money value.
 */
public final class Money implements ValueObject, Comparable<Money> {

  private final BigDecimal amount;
  private final Currency currency;
  private final ArithmeticPolicy policy;

  private Money(BigDecimal amount, Currency currency, ArithmeticPolicy policy) {
    this.currency = Validate.notNull(currency, "currency must not be null.");
    this.policy = Validate.notNull(policy, "policy must not be null.");
    BigDecimal normalized =
        policy.roundingStrategy().round(amount, policy.scalePolicy().scaleFor(currency));
    policy.overflowPolicy().check(normalized, currency);
    this.amount = normalized;
  }

  public static Money of(BigDecimal amount, Currency currency) {
    return of(amount, currency, ArithmeticPolicies.standard());
  }

  public static Money of(BigDecimal amount, Currency currency, ArithmeticPolicy policy) {
    return new Money(Validate.notNull(amount, "amount must not be null."), currency, policy);
  }

  public static Money of(long amount, Currency currency) {
    return of(BigDecimal.valueOf(amount), currency);
  }

  public static Money of(String amount, Currency currency) {
    return of(new BigDecimal(amount), currency);
  }

  public static Money zero(Currency currency) {
    return of(BigDecimal.ZERO, currency);
  }

  public BigDecimal amount() {
    return amount;
  }

  public Currency currency() {
    return currency;
  }

  public Money add(Money other) {
    return add(other, calculator());
  }

  public Money add(Money other, MoneyCalculator calculator) {
    requireSameCurrency(other);
    return new Money(calculator.add(amount, other.amount, policy), currency, policy);
  }

  public Money subtract(Money other) {
    return subtract(other, calculator());
  }

  public Money subtract(Money other, MoneyCalculator calculator) {
    requireSameCurrency(other);
    return new Money(calculator.subtract(amount, other.amount, policy), currency, policy);
  }

  public Money multiply(BigDecimal factor) {
    return multiply(factor, calculator());
  }

  public Money multiply(BigDecimal factor, MoneyCalculator calculator) {
    return new Money(calculator.multiply(amount, factor, policy), currency, policy);
  }

  public Money divide(BigDecimal divisor) {
    return divide(divisor, calculator());
  }

  public Money divide(BigDecimal divisor, MoneyCalculator calculator) {
    return new Money(calculator.divide(amount, divisor, policy), currency, policy);
  }

  public Money negate() {
    return new Money(amount.negate(), currency, policy);
  }

  public Money abs() {
    return new Money(amount.abs(), currency, policy);
  }

  public Money min(Money other) {
    requireSameCurrency(other);
    return compareTo(other) <= 0 ? this : other;
  }

  public Money max(Money other) {
    requireSameCurrency(other);
    return compareTo(other) >= 0 ? this : other;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public ArithmeticPolicy policy() {
    return policy;
  }

  /**
   * Converts this amount into {@code target} using an explicitly supplied converter — never
   * implicit.
   */
  public Money convertTo(Currency target, CurrencyConverter converter, ConversionContext context) {
    return converter.convert(this, target, context);
  }

  /**
   * Adds {@code other} after converting it into this currency — the explicit alternative to
   * same-currency {@link #add}.
   */
  public Money add(Money other, CurrencyConverter converter, ConversionContext context) {
    return add(other.convertTo(currency, converter, context));
  }

  /**
   * Subtracts {@code other} after converting it into this currency — the explicit alternative to
   * same-currency {@link #subtract}.
   */
  public Money subtract(Money other, CurrencyConverter converter, ConversionContext context) {
    return subtract(other.convertTo(currency, converter, context));
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Money that)) {
      return false;
    }
    return currency.equals(that.currency) && amount.compareTo(that.amount) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(currency, amount.stripTrailingZeros());
  }

  @Override
  public String toString() {
    return currency.code() + " " + amount;
  }

  private MoneyCalculator calculator() {
    return new DefaultMoneyCalculator(currency);
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new CurrencyMismatchException(currency.code(), other.currency.code());
    }
  }
}
