package io.genfin.document.api.brand;

public final class BrandRegistrationInformation {

  private final String taxRegistrationNumber;
  private final String businessRegistrationNumber;

  private BrandRegistrationInformation(
      String taxRegistrationNumber, String businessRegistrationNumber) {
    this.taxRegistrationNumber = taxRegistrationNumber == null ? "" : taxRegistrationNumber;
    this.businessRegistrationNumber =
        businessRegistrationNumber == null ? "" : businessRegistrationNumber;
  }

  public static BrandRegistrationInformation of(
      String taxRegistrationNumber, String businessRegistrationNumber) {
    return new BrandRegistrationInformation(taxRegistrationNumber, businessRegistrationNumber);
  }

  public String taxRegistrationNumber() {
    return taxRegistrationNumber;
  }

  public String businessRegistrationNumber() {
    return businessRegistrationNumber;
  }
}
