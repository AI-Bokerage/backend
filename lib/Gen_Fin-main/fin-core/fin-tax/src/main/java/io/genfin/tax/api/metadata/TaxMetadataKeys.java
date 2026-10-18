package io.genfin.tax.api.metadata;

/**
 * The {@code io.genfin.money.tax.TaxMetadata} attribute keys {@code IndianTaxCalculator} reads to
 * reconstruct the seller/buyer {@code PartyTaxProfile}s it needs, without fin-money's {@code
 * TaxContext} needing a fin-tax-specific field. A caller building a {@code TaxContext} to route
 * through the Tax Engine populates {@code TaxMetadata} with these keys; a caller not using the Tax
 * Engine (i.e. relying on {@code NoOpTaxCalculator}) never needs to know they exist.
 */
public final class TaxMetadataKeys {

  private TaxMetadataKeys() {}

  public static final String SELLER_COUNTRY = "tax.seller.country";
  public static final String SELLER_STATE = "tax.seller.state";
  public static final String SELLER_REGISTRATION_TYPE = "tax.seller.registrationType";
  public static final String SELLER_GST_NUMBER = "tax.seller.gstNumber";
  public static final String SELLER_LUT_NUMBER = "tax.seller.lutNumber";

  public static final String BUYER_COUNTRY = "tax.buyer.country";
  public static final String BUYER_STATE = "tax.buyer.state";
  public static final String BUYER_REGISTRATION_TYPE = "tax.buyer.registrationType";
  public static final String BUYER_GST_NUMBER = "tax.buyer.gstNumber";
  public static final String BUYER_LUT_NUMBER = "tax.buyer.lutNumber";
}
