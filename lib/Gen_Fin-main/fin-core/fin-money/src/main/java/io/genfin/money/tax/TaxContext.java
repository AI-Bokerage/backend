package io.genfin.money.tax;

import io.genfin.api.domain.ValueObject;
import java.time.Instant;

/**
 * Everything a {@code TaxCalculator} needs to compute a {@link TaxBreakdown} for one taxable
 * amount.
 */
public record TaxContext(TaxableAmount taxableAmount, TaxMetadata metadata, Instant asOf)
    implements ValueObject {}
