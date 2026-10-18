package io.genfin.tax.api.decision;

import io.genfin.api.validation.Validate;
import java.util.List;

/**
 * The six treatments Tax Engine V1's rules can resolve to (Rules 1-5 of the CPMS-oriented scope).
 */
public record StandardTaxTreatment(String code, List<TaxComponentType> components)
    implements TaxTreatment {

  public StandardTaxTreatment {
    Validate.notBlank(code, "code must not be blank.");
    components = List.copyOf(Validate.notNull(components, "components must not be null."));
  }

  public static StandardTaxTreatment of(String code, List<TaxComponentType> components) {
    return new StandardTaxTreatment(code, components);
  }

  /** Rule 1 — same-state domestic supply. */
  public static final StandardTaxTreatment CGST_SGST =
      of("CGST_SGST", List.of(StandardTaxComponentType.CGST, StandardTaxComponentType.SGST));

  /** Rule 2 — cross-state domestic supply. */
  public static final StandardTaxTreatment IGST =
      of("IGST", List.of(StandardTaxComponentType.IGST));

  /** Rule 3, LUT branch — zero-rated export. */
  public static final StandardTaxTreatment EXPORT_LUT =
      of("EXPORT_LUT", List.of(StandardTaxComponentType.EXPORT));

  /** Rule 3, IGST branch — export under payment of (refundable) IGST. */
  public static final StandardTaxTreatment EXPORT_IGST =
      of("EXPORT_IGST", List.of(StandardTaxComponentType.IGST));

  /** Rule 4 — foreign seller, Indian buyer. No calculation in V1. */
  public static final StandardTaxTreatment IMPORT_SERVICE =
      of("IMPORT_SERVICE", List.of(StandardTaxComponentType.NO_TAX));

  /** Rule 5, and the fallback for every jurisdiction pairing V1 doesn't otherwise resolve. */
  public static final StandardTaxTreatment NO_TAX =
      of("NO_TAX", List.of(StandardTaxComponentType.NO_TAX));
}
