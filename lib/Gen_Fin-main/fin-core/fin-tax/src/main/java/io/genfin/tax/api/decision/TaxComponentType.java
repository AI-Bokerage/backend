package io.genfin.tax.api.decision;

/** Open-value-type identifying one line of a tax breakdown (CGST, SGST, IGST, ...). */
public interface TaxComponentType {

  String code();
}
