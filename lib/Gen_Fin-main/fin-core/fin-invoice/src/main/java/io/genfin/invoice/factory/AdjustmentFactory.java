package io.genfin.invoice.factory;

import io.genfin.invoice.adjustment.Adjustment;
import io.genfin.invoice.adjustment.StandardAdjustmentType;
import io.genfin.invoice.builder.AdjustmentBuilder;
import io.genfin.money.money.Money;

/**
 * Convenience constructors that enforce the credit-is-negative / debit-is-positive sign convention.
 */
public final class AdjustmentFactory {

  private AdjustmentFactory() {}

  public static Adjustment credit(Money amount, String reason) {
    return build(StandardAdjustmentType.CREDIT, amount.abs().negate(), reason);
  }

  public static Adjustment debit(Money amount, String reason) {
    return build(StandardAdjustmentType.DEBIT, amount.abs(), reason);
  }

  public static Adjustment fee(Money amount, String reason) {
    return build(StandardAdjustmentType.FEE, amount.abs(), reason);
  }

  public static Adjustment penalty(Money amount, String reason) {
    return build(StandardAdjustmentType.PENALTY, amount.abs(), reason);
  }

  public static Adjustment surcharge(Money amount, String reason) {
    return build(StandardAdjustmentType.SURCHARGE, amount.abs(), reason);
  }

  public static Adjustment manual(Money signedAmount, String reason) {
    return build(StandardAdjustmentType.MANUAL, signedAmount, reason);
  }

  private static Adjustment build(StandardAdjustmentType type, Money amount, String reason) {
    return AdjustmentBuilder.newAdjustment().type(type).amount(amount).reason(reason).build();
  }
}
