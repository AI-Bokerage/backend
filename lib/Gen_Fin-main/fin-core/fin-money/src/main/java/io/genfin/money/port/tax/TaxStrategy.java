package io.genfin.money.port.tax;

import io.genfin.api.port.spi.Extension;
import io.genfin.money.tax.TaxComponent;
import io.genfin.money.tax.TaxMetadata;
import io.genfin.money.tax.TaxableAmount;

/**
 * A single tax rule (e.g. one GST slab, one VAT category) — a building block a {@link
 * TaxCalculator} composes.
 */
public interface TaxStrategy extends Extension {

  TaxComponent compute(TaxableAmount amount, TaxMetadata metadata);
}
