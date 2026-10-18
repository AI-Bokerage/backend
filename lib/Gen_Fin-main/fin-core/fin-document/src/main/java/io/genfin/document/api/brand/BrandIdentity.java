package io.genfin.document.api.brand;

import io.genfin.api.validation.Validate;

public final class BrandIdentity {

  private final String companyName;
  private final String addressLine;

  private BrandIdentity(String companyName, String addressLine) {
    Validate.notBlank(companyName, "companyName must not be blank");
    this.companyName = companyName;
    this.addressLine = addressLine == null ? "" : addressLine;
  }

  public static BrandIdentity of(String companyName, String addressLine) {
    return new BrandIdentity(companyName, addressLine);
  }

  public String companyName() {
    return companyName;
  }

  public String addressLine() {
    return addressLine;
  }
}
