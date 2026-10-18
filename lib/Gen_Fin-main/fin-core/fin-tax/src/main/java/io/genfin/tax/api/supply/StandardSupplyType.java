package io.genfin.tax.api.supply;

import io.genfin.api.validation.Validate;

public record StandardSupplyType(String code) implements SupplyType {

  public StandardSupplyType {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardSupplyType of(String code) {
    return new StandardSupplyType(code);
  }

  /** Seller and buyer in the same country and the same state. */
  public static final StandardSupplyType DOMESTIC = of("DOMESTIC");

  /** Seller and buyer in the same country, different states. */
  public static final StandardSupplyType INTERSTATE = of("INTERSTATE");

  /** Seller in the supported jurisdiction, buyer outside it. */
  public static final StandardSupplyType EXPORT = of("EXPORT");

  /** Seller outside the supported jurisdiction, buyer inside it. */
  public static final StandardSupplyType IMPORT = of("IMPORT");

  /** Neither party is in a supported jurisdiction — no nexus, always {@code NO_TAX}. */
  public static final StandardSupplyType NONE = of("NONE");
}
