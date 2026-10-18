package io.genfin.tax.api.party;

import io.genfin.api.validation.Validate;

/** The five registration standings Tax Engine V1's Indian rules distinguish between. */
public record StandardRegistrationType(String code) implements RegistrationType {

  public StandardRegistrationType {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardRegistrationType of(String code) {
    return new StandardRegistrationType(code);
  }

  /** Domestically GST-registered — has a GST number, can be charged CGST/SGST/IGST. */
  public static final StandardRegistrationType GST_REGISTERED = of("GST_REGISTERED");

  /** Domestic but below the GST registration threshold — no GST number. */
  public static final StandardRegistrationType GST_UNREGISTERED = of("GST_UNREGISTERED");

  /** Holds a Letter of Undertaking — eligible for zero-rated export without paying IGST upfront. */
  public static final StandardRegistrationType LUT_REGISTERED = of("LUT_REGISTERED");

  /** An exporter without an LUT on file — exports under payment of IGST (refundable) instead. */
  public static final StandardRegistrationType EXPORTER = of("EXPORTER");

  /** A party outside the supported jurisdiction entirely — no domestic registration applies. */
  public static final StandardRegistrationType FOREIGN_ENTITY = of("FOREIGN_ENTITY");
}
