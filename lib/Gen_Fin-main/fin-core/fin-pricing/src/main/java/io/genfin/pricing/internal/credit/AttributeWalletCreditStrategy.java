package io.genfin.pricing.internal.credit;

import io.genfin.api.validation.Validate;
import io.genfin.pricing.credit.WalletId;
import io.genfin.pricing.port.credit.CreditStrategy;
import io.genfin.pricing.pricing.PricingContext;
import io.genfin.pricing.pricing.PricingRequest;
import java.util.List;

/**
 * A {@link CreditStrategy} that reads a single {@link WalletId} from {@link
 * io.genfin.pricing.pricing.PricingAttributes}, under a configurable attribute key. Backs {@link
 * io.genfin.pricing.credit.CreditStrategies#fromAttribute()}.
 */
public final class AttributeWalletCreditStrategy implements CreditStrategy {

  private final String attributeKey;

  public AttributeWalletCreditStrategy(String attributeKey) {
    this.attributeKey = Validate.notBlank(attributeKey, "attributeKey must not be blank.");
  }

  @Override
  public boolean supports(PricingRequest.Line line, PricingContext context) {
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return context.attributes().find(attributeKey).isPresent();
  }

  @Override
  public List<WalletId> resolve(PricingRequest.Line line, PricingContext context) {
    Validate.notNull(line, "line must not be null.");
    Validate.notNull(context, "context must not be null.");
    return context
        .attributes()
        .find(attributeKey)
        .map(value -> List.of(WalletId.of(value)))
        .orElse(List.of());
  }
}
