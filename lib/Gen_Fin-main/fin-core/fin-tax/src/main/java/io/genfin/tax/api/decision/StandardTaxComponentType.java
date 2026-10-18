package io.genfin.tax.api.decision;

import io.genfin.api.validation.Validate;

/**
 * The five component types Tax Engine V1 supports — every {@link TaxTreatment} maps to one or more
 * of these.
 */
public record StandardTaxComponentType(String code) implements TaxComponentType {

  public StandardTaxComponentType {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardTaxComponentType of(String code) {
    return new StandardTaxComponentType(code);
  }

  public static final StandardTaxComponentType CGST = of("CGST");
  public static final StandardTaxComponentType SGST = of("SGST");
  public static final StandardTaxComponentType IGST = of("IGST");

  /** A zero-rated export line (export under LUT) — kept explicit rather than an empty breakdown. */
  public static final StandardTaxComponentType EXPORT = of("EXPORT");

  /**
   * An explicit zero-tax line for scenarios with no applicable component (foreign↔foreign, import
   * service).
   */
  public static final StandardTaxComponentType NO_TAX = of("NO_TAX");
}
