package io.genfin.money.tax;

/**
 * A jurisdiction-defined classification of taxable items (e.g. a GST slab, a VAT category). Opaque
 * here by design.
 */
public interface TaxCategory {

  String code();
}
