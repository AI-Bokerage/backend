package io.genfin.money.port.tax;

import io.genfin.api.port.spi.Resolver;
import io.genfin.money.tax.TaxContext;

/**
 * Resolves which {@link TaxCalculator} applies to a given context (e.g. by jurisdiction, by product
 * type).
 */
public interface TaxPolicy extends Resolver<TaxContext, TaxCalculator> {}
