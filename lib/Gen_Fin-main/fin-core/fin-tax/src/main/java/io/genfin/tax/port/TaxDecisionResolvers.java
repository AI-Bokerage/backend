package io.genfin.tax.port;

import io.genfin.tax.api.config.ExportTreatment;
import io.genfin.tax.api.config.IndianTaxConfiguration;
import io.genfin.tax.internal.decision.DefaultDomesticPolicy;
import io.genfin.tax.internal.decision.DefaultExportPolicy;
import io.genfin.tax.internal.decision.DefaultImportPolicy;
import io.genfin.tax.internal.decision.DefaultRegistrationPolicy;
import io.genfin.tax.internal.decision.DefaultTaxDecisionResolver;

/**
 * Factory for a standard, ready-to-use {@link TaxDecisionResolver}. This is the only
 * consumer-reachable way to obtain a working resolver — the concrete policy implementations all
 * live in {@code internal}, never exported.
 */
public final class TaxDecisionResolvers {

  private TaxDecisionResolvers() {}

  /**
   * Wires the default policies using {@link IndianTaxConfiguration#standard()}'s export treatment.
   */
  public static TaxDecisionResolver standard() {
    return of(ExportTreatment.EXPORT_WITH_LUT);
  }

  public static TaxDecisionResolver of(ExportTreatment defaultExportTreatment) {
    return new DefaultTaxDecisionResolver(
        new DefaultRegistrationPolicy(),
        new DefaultDomesticPolicy(),
        new DefaultExportPolicy(defaultExportTreatment),
        new DefaultImportPolicy());
  }
}
