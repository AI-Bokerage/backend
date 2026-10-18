package io.genfin.pricing.pricing;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.pricing.id.PricingRequestId;
import io.genfin.pricing.id.PricingResultId;
import java.time.Instant;

/**
 * The calculated commercial value produced by a run of the Pricing Pipeline for one {@link
 * PricingRequest}. Immutable and final: once produced it is never mutated, only ever superseded by
 * a new {@code PricingResult} carrying a later {@link PricingVersion} for the same request. Later
 * modules (Invoice, Payment, Ledger) consume this - {@code fin-pricing} has no knowledge of, or
 * dependency on, any of them.
 */
public record PricingResult(
    PricingResultId id,
    PricingRequestId requestId,
    PricingVersion version,
    PricingSummary summary,
    PricingMetadata metadata,
    Instant calculatedAt)
    implements ValueObject {

  public PricingResult {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(requestId, "requestId must not be null.");
    Validate.notNull(version, "version must not be null.");
    Validate.notNull(summary, "summary must not be null.");
    Validate.notNull(metadata, "metadata must not be null.");
    Validate.notNull(calculatedAt, "calculatedAt must not be null.");
  }
}
