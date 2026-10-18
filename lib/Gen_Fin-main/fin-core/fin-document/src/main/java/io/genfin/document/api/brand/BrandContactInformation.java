package io.genfin.document.api.brand;

public final class BrandContactInformation {

  private final String phone;
  private final String email;
  private final String website;

  private BrandContactInformation(String phone, String email, String website) {
    this.phone = phone == null ? "" : phone;
    this.email = email == null ? "" : email;
    this.website = website == null ? "" : website;
  }

  public static BrandContactInformation of(String phone, String email, String website) {
    return new BrandContactInformation(phone, email, website);
  }

  public String phone() {
    return phone;
  }

  public String email() {
    return email;
  }

  public String website() {
    return website;
  }
}
