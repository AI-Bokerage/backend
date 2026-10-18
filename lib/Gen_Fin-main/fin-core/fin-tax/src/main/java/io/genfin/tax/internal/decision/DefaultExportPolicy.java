package io.genfin.tax.internal.decision;

import io.genfin.api.validation.Validate;
import io.genfin.tax.api.config.ExportTreatment;
import io.genfin.tax.api.decision.StandardTaxTreatment;
import io.genfin.tax.api.decision.TaxTreatment;
import io.genfin.tax.api.party.PartyTaxProfile;
import io.genfin.tax.api.party.StandardRegistrationType;
import io.genfin.tax.port.ExportPolicy;

/**
 * Rule 3: an {@code LUT_REGISTERED} seller always exports zero-rated; an {@code EXPORTER}-flagged
 * seller without an LUT always pays IGST; any other seller falls back to the configured default —
 * never hardcoded.
 */
public final class DefaultExportPolicy implements ExportPolicy {

  private final ExportTreatment defaultTreatment;

  public DefaultExportPolicy(ExportTreatment defaultTreatment) {
    this.defaultTreatment =
        Validate.notNull(defaultTreatment, "defaultTreatment must not be null.");
  }

  @Override
  public TaxTreatment decide(PartyTaxProfile seller, PartyTaxProfile buyer) {
    if (StandardRegistrationType.LUT_REGISTERED.code().equals(seller.registrationType().code())) {
      return StandardTaxTreatment.EXPORT_LUT;
    }
    if (StandardRegistrationType.EXPORTER.code().equals(seller.registrationType().code())) {
      return StandardTaxTreatment.EXPORT_IGST;
    }
    return defaultTreatment == ExportTreatment.EXPORT_WITH_LUT
        ? StandardTaxTreatment.EXPORT_LUT
        : StandardTaxTreatment.EXPORT_IGST;
  }
}
