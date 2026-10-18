package io.genfin.reconciliation.discrepancy;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;

/**
 * Describes a discrepancy reason *kind* — its human-readable label — as opposed to the bare {@link
 * DiscrepancyReason} code carried on a {@link Discrepancy} itself.
 */
public record DiscrepancyReasonDescriptor(DiscrepancyReason reason, String displayName)
    implements ValueObject {

  public DiscrepancyReasonDescriptor {
    Validate.notNull(reason, "reason must not be null.");
    Validate.notBlank(displayName, "displayName must not be blank.");
  }
}
