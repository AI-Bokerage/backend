package io.genfin.pricing.credit;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import io.genfin.money.money.Money;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link CreditWallet}'s spendable balance at one point in time - a net {@link Money} total, plus
 * a per-{@link CreditCategory} (keyed by {@link CreditCategory#code()}) breakdown, in the order its
 * {@link Credit}s were granted. The read model {@link CreditCalculator} consumes and {@link
 * io.genfin.pricing.internal.credit.DefaultCreditPolicy} apportions a draw across. Mirrors {@code
 * io.genfin.pricing.price.PriceBreakdown}'s "sum of settled components" shape.
 */
public record CreditBalance(WalletId walletId, Money total, Map<String, Money> byCategory)
    implements ValueObject {

  public CreditBalance {
    Validate.notNull(walletId, "walletId must not be null.");
    Validate.notNull(total, "total must not be null.");
    Validate.notNull(byCategory, "byCategory must not be null.");
    byCategory = Map.copyOf(byCategory);
  }

  /** No spendable credits at all. */
  public static CreditBalance empty(WalletId walletId, Currency currency) {
    return new CreditBalance(walletId, Money.zero(currency), Map.of());
  }

  /** Sums {@code credits} into a total and a per-category breakdown. */
  public static CreditBalance of(WalletId walletId, List<Credit> credits, Currency currency) {
    Validate.notNull(credits, "credits must not be null.");
    Money total = Money.zero(currency);
    Map<String, Money> byCategory = new LinkedHashMap<>();
    for (Credit credit : credits) {
      total = total.add(credit.amount());
      byCategory.merge(credit.category().code(), credit.amount(), Money::add);
    }
    return new CreditBalance(walletId, total, byCategory);
  }

  /** How much of {@code total} is attributable to {@code category}, or zero if none. */
  public Money availableFor(CreditCategory category) {
    Validate.notNull(category, "category must not be null.");
    return byCategory.getOrDefault(category.code(), Money.zero(total.currency()));
  }

  public boolean isEmpty() {
    return total.isZero();
  }
}
