package io.genfin.pricing.discount;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.pricing.internal.discount.DefaultDiscountValidator;
import io.genfin.pricing.port.discount.DiscountValidator;

/**
 * Factory for {@link DiscountValidator} instances. Mirrors {@code
 * io.genfin.ledger.validation.Validators}.
 */
public final class DiscountValidators {

  private DiscountValidators() {}

  /** The default structural check: a discounted line's net amount must not go negative. */
  public static DiscountValidator standard() {
    return new DefaultDiscountValidator();
  }

  /**
   * Resolves the {@link DiscountValidator} registered in {@code registry}, or {@link #standard}.
   */
  public static DiscountValidator from(ExtensionRegistry registry) {
    return registry.find(DiscountValidator.class).orElseGet(DiscountValidators::standard);
  }
}
