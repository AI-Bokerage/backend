package io.genfin.invoice.adjustment;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.invoice.metadata.Metadata;
import io.genfin.money.money.Money;

/**
 * A single invoice-total adjustment. {@code amount} is signed: positive increases the invoice total
 * (debit/fee/penalty/surcharge), negative decreases it (credit) — the type is a classification
 * label, not a sign rule, since a "correction" or "manual" adjustment may go either way.
 */
public record Adjustment(AdjustmentType type, Money amount, String reason, Metadata metadata)
    implements ValueObject {

  public Adjustment {
    Validate.notNull(type, "type must not be null.");
    Validate.notNull(amount, "amount must not be null.");
    if (metadata == null) {
      metadata = Metadata.empty();
    }
  }

  public static Adjustment of(AdjustmentType type, Money amount, String reason) {
    return new Adjustment(type, amount, reason, Metadata.empty());
  }
}
