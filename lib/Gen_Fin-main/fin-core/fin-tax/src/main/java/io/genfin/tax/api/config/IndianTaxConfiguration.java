package io.genfin.tax.api.config;

import io.genfin.api.validation.Validate;

/** Top-level configuration for the Indian jurisdiction's default rules. */
public record IndianTaxConfiguration(
    ExportTreatment defaultExportTreatment, RateConfiguration rates) {

  public IndianTaxConfiguration {
    Validate.notNull(defaultExportTreatment, "defaultExportTreatment must not be null.");
    Validate.notNull(rates, "rates must not be null.");
  }

  /** {@code EXPORT_WITH_LUT} default, standard Indian GST rates. */
  public static IndianTaxConfiguration standard() {
    return new IndianTaxConfiguration(
        ExportTreatment.EXPORT_WITH_LUT, RateConfiguration.defaultIndianRates());
  }

  public static IndianTaxConfiguration of(
      ExportTreatment defaultExportTreatment, RateConfiguration rates) {
    return new IndianTaxConfiguration(defaultExportTreatment, rates);
  }
}
