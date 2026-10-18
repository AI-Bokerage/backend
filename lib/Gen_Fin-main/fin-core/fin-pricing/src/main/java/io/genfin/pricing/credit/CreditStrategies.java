package io.genfin.pricing.credit;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.internal.credit.AttributeWalletCreditStrategy;
import io.genfin.pricing.port.credit.CreditStrategy;

/**
 * Factory for {@link CreditStrategy} instances. {@link #fromAttribute()} is the standard way a
 * resolved wallet reaches the Credit Engine - via {@link
 * io.genfin.pricing.pricing.PricingAttributes} - which an application populates from whatever
 * customer/account context it holds; fin-pricing has no other opinion on where wallet ids come
 * from.
 */
public final class CreditStrategies {

  private CreditStrategies() {}

  /** Reads the resolved {@link WalletId} from {@link WalletId#ATTRIBUTE_KEY}. */
  public static CreditStrategy fromAttribute() {
    return fromAttribute(WalletId.ATTRIBUTE_KEY);
  }

  /** Reads the resolved {@link WalletId} from {@code attributeKey}. */
  public static CreditStrategy fromAttribute(String attributeKey) {
    Validate.notBlank(attributeKey, "attributeKey must not be blank.");
    return new AttributeWalletCreditStrategy(attributeKey);
  }
}
