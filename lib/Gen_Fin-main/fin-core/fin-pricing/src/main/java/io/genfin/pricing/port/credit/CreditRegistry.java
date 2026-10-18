package io.genfin.pricing.port.credit;

import io.genfin.api.exception.ValidationException;
import io.genfin.pricing.credit.CreditAllocation;
import io.genfin.pricing.credit.CreditUsage;
import io.genfin.pricing.credit.CreditWallet;
import io.genfin.pricing.credit.WalletId;
import java.util.List;
import java.util.Optional;

/**
 * Registry of the {@link CreditWallet}s an application has published for pricing. fin-pricing ships
 * no built-in wallets - an application registers each one it manages and the Pricing Pipeline's
 * Credit Engine stage looks it up by {@link WalletId}. Also the read model for {@link CreditUsage}
 * and the write model for {@link CreditAllocation} - fin-pricing never counts or persists
 * allocations itself; it only produces the {@link CreditAllocation} fact and leaves recording it to
 * the consuming application. Mirrors {@code io.genfin.pricing.port.coupon.CouponRegistry}.
 */
public interface CreditRegistry {

  void register(CreditWallet wallet);

  Optional<CreditWallet> find(WalletId id);

  default CreditWallet require(WalletId id) {
    return find(id)
        .orElseThrow(() -> new ValidationException("Unregistered credit wallet: " + id.value()));
  }

  /** Current usage of {@code id}'s wallet capacity. */
  CreditUsage usageOf(WalletId id);

  /** Records {@code allocation}, counting it against its wallet's {@link CreditUsage}. */
  void recordAllocation(CreditAllocation allocation);

  List<CreditWallet> findAll();
}
