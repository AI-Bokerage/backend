package io.genfin.pricing.internal.credit;

import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.credit.CreditAllocation;
import io.genfin.pricing.credit.CreditBalance;
import io.genfin.pricing.credit.CreditCalculator;
import io.genfin.pricing.credit.CreditResult;
import io.genfin.pricing.credit.CreditWallet;
import io.genfin.pricing.credit.WalletId;
import io.genfin.pricing.port.credit.CreditPolicy;
import io.genfin.pricing.port.credit.CreditRegistry;
import io.genfin.pricing.port.credit.CreditStrategy;
import io.genfin.pricing.price.Price;
import io.genfin.pricing.price.PriceAdjustment;
import io.genfin.pricing.price.PriceBreakdown;
import io.genfin.pricing.price.PriceComponent;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Gathers every {@link WalletId} each configured {@link CreditStrategy} that {@link
 * CreditStrategy#supports supports} the line offers, then tries each in order: the first wallet
 * that is registered and carries a non-empty {@link CreditBalance} wins - unlike the Discount
 * Engine, which stacks every match, at most one wallet is ever drawn against per line. A line with
 * no matching, funded wallet simply carries no credit. Mirrors {@code
 * io.genfin.pricing.internal.coupon.DefaultCouponPolicy}'s "gather candidates, delegate the
 * winner-pick" shape.
 */
public final class DefaultCreditPolicy implements CreditPolicy {

  private final CreditRegistry registry;
  private final List<CreditStrategy> strategies;
  private final CreditCalculator calculator;

  public DefaultCreditPolicy(
      CreditRegistry registry, List<CreditStrategy> strategies, CreditCalculator calculator) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
    this.strategies = List.copyOf(strategies);
    this.calculator = Validate.notNull(calculator, "calculator must not be null.");
  }

  @Override
  public List<CreditResult> apply(
      List<Price> prices, List<PricingRequest.Line> lines, PricingContext context) {
    Validate.notNull(prices, "prices must not be null.");
    Validate.notNull(lines, "lines must not be null.");
    Validate.notNull(context, "context must not be null.");
    Validate.argument(prices.size() == lines.size(), "prices and lines must be the same size.");
    List<CreditResult> results = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      results.add(applyTo(prices.get(i), lines.get(i), context));
    }
    return results;
  }

  private CreditResult applyTo(Price price, PricingRequest.Line line, PricingContext context) {
    for (WalletId walletId : candidatesFor(line, context)) {
      Optional<CreditWallet> wallet = registry.find(walletId);
      if (wallet.isPresent()) {
        CreditBalance balance = wallet.get().balanceAt(Instant.now(), price.amount().currency());
        if (!balance.isEmpty()) {
          return applyCredit(price, balance, line, context);
        }
      }
    }
    return CreditResult.unchanged(price);
  }

  private CreditResult applyCredit(
      Price price, CreditBalance balance, PricingRequest.Line line, PricingContext context) {
    Money runningAmount = price.amount();
    PriceAdjustment adjustment = calculator.applyTo(runningAmount, balance, line, context);
    if (adjustment.amount().isZero()) {
      return CreditResult.unchanged(price);
    }
    List<PriceComponent> components = new ArrayList<>(price.breakdown().components());
    components.add(adjustment.toComponent("Wallet credit " + balance.walletId().value()));
    Price applied = new Price(price.catalogId(), new PriceBreakdown(components));
    Money drawn = adjustment.amount().abs();
    CreditAllocation allocation =
        CreditAllocation.of(
            balance.walletId(), drawn, apportion(drawn, balance), context.requestId());
    return CreditResult.applied(applied, allocation);
  }

  /**
   * Turns {@code drawnTotal} into a per-category breakdown by walking {@code balance}'s categories
   * in the order its credits were granted, taking as much as each has available until the draw is
   * fully accounted for.
   *
   * <p>ponytail: first-funded-category-first, no configurable draw-down priority across categories;
   * add a {@code CreditPriorityPolicy} extension point if an application needs e.g. "gift before
   * promotional" ordering.
   */
  private Map<String, Money> apportion(Money drawnTotal, CreditBalance balance) {
    Map<String, Money> result = new LinkedHashMap<>();
    Money remaining = drawnTotal;
    for (Map.Entry<String, Money> entry : balance.byCategory().entrySet()) {
      if (remaining.isZero()) {
        break;
      }
      Money take = entry.getValue().min(remaining);
      if (take.isPositive()) {
        result.put(entry.getKey(), take);
        remaining = remaining.subtract(take);
      }
    }
    return result;
  }

  private List<WalletId> candidatesFor(PricingRequest.Line line, PricingContext context) {
    List<WalletId> candidates = new ArrayList<>();
    for (CreditStrategy strategy : strategies) {
      if (strategy.supports(line, context)) {
        candidates.addAll(strategy.resolve(line, context));
      }
    }
    return candidates;
  }
}
