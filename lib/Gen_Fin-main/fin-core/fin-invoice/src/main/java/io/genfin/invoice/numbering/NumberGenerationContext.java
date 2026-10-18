package io.genfin.invoice.numbering;

import io.genfin.money.currency.Currency;
import java.time.Instant;

/**
 * Everything an {@code InvoiceNumberGenerator} may need — region/tenant partitioning is expressed
 * as an opaque {@code scopeKey}.
 */
public record NumberGenerationContext(Instant asOf, String scopeKey, Currency currency) {}
