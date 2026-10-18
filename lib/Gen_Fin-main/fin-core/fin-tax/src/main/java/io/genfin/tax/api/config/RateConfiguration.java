package io.genfin.tax.api.config;

import io.genfin.api.validation.Validate;
import io.genfin.money.percentage.Percentage;
import io.genfin.tax.api.decision.StandardTaxComponentType;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, application-overridable rate table, keyed by {@code TaxComponentType.code()}. Rates
 * are never hardcoded inside the engine itself — every lookup goes through this configuration via
 * {@code TaxRateProvider}.
 */
public record RateConfiguration(Map<String, Percentage> ratesByComponentCode) {

  public RateConfiguration {
    Validate.notNull(ratesByComponentCode, "ratesByComponentCode must not be null.");
    ratesByComponentCode = Map.copyOf(ratesByComponentCode);
  }

  public Optional<Percentage> rateFor(String componentCode) {
    return Optional.ofNullable(ratesByComponentCode.get(componentCode));
  }

  /** CGST 9% + SGST 9% (= 18% domestic same-state), IGST 18%, EXPORT/NO_TAX both 0%. */
  public static RateConfiguration defaultIndianRates() {
    return new RateConfiguration(
        Map.of(
            StandardTaxComponentType.CGST.code(), Percentage.ofPercent(new BigDecimal("9")),
            StandardTaxComponentType.SGST.code(), Percentage.ofPercent(new BigDecimal("9")),
            StandardTaxComponentType.IGST.code(), Percentage.ofPercent(new BigDecimal("18")),
            StandardTaxComponentType.EXPORT.code(), Percentage.ofPercent(BigDecimal.ZERO),
            StandardTaxComponentType.NO_TAX.code(), Percentage.ofPercent(BigDecimal.ZERO)));
  }
}
