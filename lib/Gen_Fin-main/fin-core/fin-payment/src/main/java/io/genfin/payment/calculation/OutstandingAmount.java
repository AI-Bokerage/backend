package io.genfin.payment.calculation;

import io.genfin.api.domain.ValueObject;
import io.genfin.money.money.Money;

/** The amount still owed — clamped to zero (an overpayment is never a *negative* amount owed). */
public record OutstandingAmount(Money amount) implements ValueObject {

  public static OutstandingAmount from(PaymentBalance balance) {
    return new OutstandingAmount(
        balance.isUnderpaid() ? balance.remaining() : Money.zero(balance.remaining().currency()));
  }
}
