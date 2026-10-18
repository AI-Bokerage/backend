package io.genfin.money.tax;

import io.genfin.api.domain.ValueObject;

/**
 * The audit-friendly pairing of the input {@link TaxContext} with the {@link TaxBreakdown} it
 * produced.
 */
public record TaxComputationResult(TaxContext context, TaxBreakdown breakdown)
    implements ValueObject {}
