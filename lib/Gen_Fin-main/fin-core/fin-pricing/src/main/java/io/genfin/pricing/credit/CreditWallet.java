package io.genfin.pricing.credit;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.money.currency.Currency;
import java.time.Instant;
import java.util.List;

/**
 * One customer's (or account's) pool of {@link Credit} grants, spanning however many {@link
 * CreditCategory} taxonomies the consuming application uses (wallet, gift, promotional, account, or
 * its own). fin-pricing never persists this itself - a {@link
 * io.genfin.pricing.port.credit.CreditRegistry} implementation holds it on the application's
 * behalf, and the Credit Engine pipeline stage only reads its point-in-time {@link CreditBalance}.
 */
public record CreditWallet(WalletId id, List<Credit> credits) implements ValueObject {

  public CreditWallet {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(credits, "credits must not be null.");
    credits = List.copyOf(credits);
  }

  public static CreditWallet of(WalletId id, List<Credit> credits) {
    return new CreditWallet(id, credits);
  }

  public static CreditWallet empty(WalletId id) {
    return new CreditWallet(id, List.of());
  }

  /** Every {@link Credit} not yet expired as of {@code instant}. */
  public List<Credit> activeCreditsAt(Instant instant) {
    Validate.notNull(instant, "instant must not be null.");
    return credits.stream().filter(credit -> !credit.isExpiredAt(instant)).toList();
  }

  /** This wallet's spendable {@link CreditBalance} as of {@code instant}. */
  public CreditBalance balanceAt(Instant instant, Currency currency) {
    return CreditBalance.of(id, activeCreditsAt(instant), currency);
  }
}
