package io.genfin.pricing.credit;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.money.Money;
import io.genfin.pricing.id.CreditId;
import java.time.Instant;
import java.util.Optional;

/**
 * One grant of credit sitting in a {@link CreditWallet} - identity, the {@link CreditCategory} it
 * belongs to, its {@link Money} amount, and an optional expiry. fin-pricing ships no credit
 * arithmetic of its own here: turning a wallet's available {@link Credit}s into a price reduction
 * is {@link CreditCalculator}'s concern, and which credits an application grants (and why) is
 * entirely the consuming application's business rule.
 */
public record Credit(
    CreditId id, CreditCategory category, Money amount, Optional<Instant> expiresAt)
    implements ValueObject {

  public Credit {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(category, "category must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    Validate.argument(amount.isPositive(), "amount must be positive.");
    Validate.notNull(expiresAt, "expiresAt must not be null.");
  }

  /** A grant with no expiry. */
  public static Credit of(CreditCategory category, Money amount) {
    return new Credit(CreditId.generate(), category, amount, Optional.empty());
  }

  /** A grant that expires at {@code expiresAt}. */
  public static Credit expiring(CreditCategory category, Money amount, Instant expiresAt) {
    Validate.notNull(expiresAt, "expiresAt must not be null.");
    return new Credit(CreditId.generate(), category, amount, Optional.of(expiresAt));
  }

  /** Whether this grant is no longer spendable at {@code instant}. */
  public boolean isExpiredAt(Instant instant) {
    Validate.notNull(instant, "instant must not be null.");
    return expiresAt.map(instant::isAfter).orElse(false);
  }
}
