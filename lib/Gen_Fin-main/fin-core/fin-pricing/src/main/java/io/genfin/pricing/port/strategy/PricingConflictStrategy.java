package io.genfin.pricing.port.strategy;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.money.Money;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.strategy.PricingCandidate;
import java.util.List;

/**
 * One application-selectable way of resolving which {@link PricingCandidate}(s) survive when more
 * than one discount, promotion or coupon is simultaneously eligible for the same line - Highest
 * Discount, Best Price, First Applicable, Priority Order, Maximum Savings and Exclusive Promotion
 * (see {@code io.genfin.pricing.strategy.PricingStrategies}) are fin-pricing's shipped answers.
 * Distinct from {@link io.genfin.pricing.port.calculation.PricingStrategy}, which resolves one
 * line's base {@code Price} rather than choosing among competing reductions. Mirrors {@code
 * io.genfin.pricing.port.discount.DiscountStrategy}'s Extension-based pluggability.
 */
public interface PricingConflictStrategy extends Extension {

  /**
   * Which of {@code candidates} apply, given {@code runningAmount} (the line's price before any of
   * them are applied) and {@code context}. An empty list means none applies; most strategies return
   * exactly one winner, though a stacking-oriented strategy (e.g. Maximum Savings) may return more
   * than one.
   */
  List<PricingCandidate> select(
      List<PricingCandidate> candidates, Money runningAmount, PricingContext context);
}
