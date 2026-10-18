package io.genfin.tax.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.result.Result;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.tax.TaxCategory;
import io.genfin.money.tax.TaxComputationResult;
import io.genfin.money.tax.TaxContext;
import io.genfin.money.tax.TaxMetadata;
import io.genfin.money.tax.TaxableAmount;
import io.genfin.tax.api.config.ExportTreatment;
import io.genfin.tax.api.config.RateConfiguration;
import io.genfin.tax.api.exception.TaxErrorCode;
import io.genfin.tax.api.metadata.TaxMetadataKeys;
import io.genfin.tax.api.party.StandardRegistrationType;
import io.genfin.tax.internal.decision.DefaultDomesticPolicy;
import io.genfin.tax.internal.decision.DefaultExportPolicy;
import io.genfin.tax.internal.decision.DefaultImportPolicy;
import io.genfin.tax.internal.decision.DefaultRegistrationPolicy;
import io.genfin.tax.internal.decision.DefaultTaxDecisionResolver;
import io.genfin.tax.internal.rate.DefaultTaxRateProvider;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves the "backward compatibility" guarantee: a caller that never populates {@link
 * TaxMetadataKeys} gets exactly the same zero-tax result {@code NoOpTaxCalculator} always gave,
 * while a genuine engine misconfiguration still surfaces as a real failure.
 */
class DelegatingTaxCalculatorTest {

  private static final Currency INR =
      CurrencyFactory.newCurrency()
          .code("INR")
          .symbol("₹")
          .displayName("Indian Rupee")
          .fractionDigits(2)
          .build();
  private static final TaxCategory STANDARD_CATEGORY = () -> "STANDARD";

  private final IndianTaxCalculator engine =
      new IndianTaxCalculator(
          new DefaultTaxDecisionResolver(
              new DefaultRegistrationPolicy(), new DefaultDomesticPolicy(),
              new DefaultExportPolicy(ExportTreatment.EXPORT_WITH_LUT), new DefaultImportPolicy()),
          new DefaultTaxRateProvider(RateConfiguration.defaultIndianRates()));
  private final DelegatingTaxCalculator delegating = new DelegatingTaxCalculator(engine);

  private TaxContext contextWith(Map<String, String> attributes) {
    TaxableAmount taxableAmount = new TaxableAmount(Money.of("1000", INR), STANDARD_CATEGORY);
    return new TaxContext(taxableAmount, new TaxMetadata(attributes), Instant.now());
  }

  @Test
  void fallsBackToZeroTaxWhenNoTaxProfileSupplied() {
    Result<TaxComputationResult> result = delegating.calculate(contextWith(Map.of()));

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().breakdown().totalTax().amount()).isEqualByComparingTo("0.00");
    assertThat(result.get().breakdown().components()).isEmpty();
  }

  @Test
  void delegatesToEngineWhenTaxProfileIsSupplied() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put(TaxMetadataKeys.SELLER_COUNTRY, "IN");
    attrs.put(TaxMetadataKeys.SELLER_STATE, "MH");
    attrs.put(
        TaxMetadataKeys.SELLER_REGISTRATION_TYPE, StandardRegistrationType.GST_REGISTERED.code());
    attrs.put(TaxMetadataKeys.SELLER_GST_NUMBER, "27SELLER1234F1Z5");
    attrs.put(TaxMetadataKeys.BUYER_COUNTRY, "IN");
    attrs.put(TaxMetadataKeys.BUYER_STATE, "MH");
    attrs.put(
        TaxMetadataKeys.BUYER_REGISTRATION_TYPE, StandardRegistrationType.GST_REGISTERED.code());
    attrs.put(TaxMetadataKeys.BUYER_GST_NUMBER, "27BUYER01234F1Z5");

    Result<TaxComputationResult> result = delegating.calculate(contextWith(attrs));

    assertThat(result.get().breakdown().totalTax().amount()).isEqualByComparingTo("180.00");
  }

  @Test
  void propagatesGenuineMisconfigurationRatherThanFallingBack() {
    IndianTaxCalculator engineWithNoRates =
        new IndianTaxCalculator(
            new DefaultTaxDecisionResolver(
                new DefaultRegistrationPolicy(), new DefaultDomesticPolicy(),
                new DefaultExportPolicy(ExportTreatment.EXPORT_WITH_LUT),
                    new DefaultImportPolicy()),
            new DefaultTaxRateProvider(new RateConfiguration(Map.of())));
    DelegatingTaxCalculator delegatingWithBrokenEngine =
        new DelegatingTaxCalculator(engineWithNoRates);

    Map<String, String> attrs = new HashMap<>();
    attrs.put(TaxMetadataKeys.SELLER_COUNTRY, "IN");
    attrs.put(TaxMetadataKeys.SELLER_STATE, "MH");
    attrs.put(
        TaxMetadataKeys.SELLER_REGISTRATION_TYPE, StandardRegistrationType.GST_REGISTERED.code());
    attrs.put(TaxMetadataKeys.SELLER_GST_NUMBER, "27SELLER1234F1Z5");
    attrs.put(TaxMetadataKeys.BUYER_COUNTRY, "IN");
    attrs.put(TaxMetadataKeys.BUYER_STATE, "MH");
    attrs.put(
        TaxMetadataKeys.BUYER_REGISTRATION_TYPE, StandardRegistrationType.GST_REGISTERED.code());
    attrs.put(TaxMetadataKeys.BUYER_GST_NUMBER, "27BUYER01234F1Z5");

    Result<TaxComputationResult> result = delegatingWithBrokenEngine.calculate(contextWith(attrs));

    assertThat(result.isFailure()).isTrue();
    result.onFailure(
        failure -> assertThat(failure.errorCode()).isEqualTo(TaxErrorCode.UNSUPPORTED_TRANSACTION));
  }
}
