package io.genfin.tax.internal.calculation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.result.Result;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.money.tax.TaxBreakdown;
import io.genfin.money.tax.TaxCategory;
import io.genfin.money.tax.TaxComputationResult;
import io.genfin.money.tax.TaxContext;
import io.genfin.money.tax.TaxMetadata;
import io.genfin.money.tax.TaxableAmount;
import io.genfin.tax.api.config.ExportTreatment;
import io.genfin.tax.api.config.RateConfiguration;
import io.genfin.tax.api.decision.StandardTaxComponentType;
import io.genfin.tax.api.exception.TaxErrorCode;
import io.genfin.tax.api.metadata.TaxMetadataKeys;
import io.genfin.tax.api.party.StandardRegistrationType;
import io.genfin.tax.internal.decision.DefaultDomesticPolicy;
import io.genfin.tax.internal.decision.DefaultExportPolicy;
import io.genfin.tax.internal.decision.DefaultImportPolicy;
import io.genfin.tax.internal.decision.DefaultRegistrationPolicy;
import io.genfin.tax.internal.decision.DefaultTaxDecisionResolver;
import io.genfin.tax.internal.rate.DefaultTaxRateProvider;
import io.genfin.tax.port.TaxDecisionResolver;
import io.genfin.tax.port.TaxRateProvider;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ₹1000 @ 18% split: same-state → CGST 90 + SGST 90; interstate → IGST 180; export LUT → 0; export
 * IGST → 180 (paid, refundable); foreign↔India / foreign↔foreign → 0, per the design's worked
 * examples.
 */
class IndianTaxCalculatorTest {

  private static final Currency INR =
      CurrencyFactory.newCurrency()
          .code("INR")
          .symbol("₹")
          .displayName("Indian Rupee")
          .fractionDigits(2)
          .build();
  private static final TaxCategory STANDARD_CATEGORY = () -> "STANDARD";

  private final TaxDecisionResolver resolver =
      new DefaultTaxDecisionResolver(
          new DefaultRegistrationPolicy(), new DefaultDomesticPolicy(),
          new DefaultExportPolicy(ExportTreatment.EXPORT_WITH_LUT), new DefaultImportPolicy());
  private final TaxRateProvider rateProvider =
      new DefaultTaxRateProvider(RateConfiguration.defaultIndianRates());
  private final IndianTaxCalculator calculator = new IndianTaxCalculator(resolver, rateProvider);

  private TaxContext contextFor(Map<String, String> attributes) {
    TaxableAmount taxableAmount = new TaxableAmount(Money.of("1000", INR), STANDARD_CATEGORY);
    return new TaxContext(taxableAmount, new TaxMetadata(attributes), Instant.now());
  }

  private Map<String, String> domesticSameState() {
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
    return attrs;
  }

  @Test
  void sameStateComputesCgstAndSgstNinetyEach() {
    Result<TaxComputationResult> result = calculator.calculate(contextFor(domesticSameState()));

    assertThat(result.isSuccess()).isTrue();
    TaxBreakdown breakdown = result.get().breakdown();
    assertThat(breakdown.components()).hasSize(2);
    assertThat(componentAmount(breakdown, StandardTaxComponentType.CGST))
        .isEqualByComparingTo("90.00");
    assertThat(componentAmount(breakdown, StandardTaxComponentType.SGST))
        .isEqualByComparingTo("90.00");
    assertThat(breakdown.totalTax().amount()).isEqualByComparingTo("180.00");
  }

  @Test
  void differentStateComputesIgstOneEighty() {
    Map<String, String> attrs = domesticSameState();
    attrs.put(TaxMetadataKeys.BUYER_STATE, "KA");

    Result<TaxComputationResult> result = calculator.calculate(contextFor(attrs));

    TaxBreakdown breakdown = result.get().breakdown();
    assertThat(breakdown.components()).hasSize(1);
    assertThat(componentAmount(breakdown, StandardTaxComponentType.IGST))
        .isEqualByComparingTo("180.00");
  }

  @Test
  void exportUnderLutComputesZero() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put(TaxMetadataKeys.SELLER_COUNTRY, "IN");
    attrs.put(TaxMetadataKeys.SELLER_STATE, "MH");
    attrs.put(
        TaxMetadataKeys.SELLER_REGISTRATION_TYPE, StandardRegistrationType.LUT_REGISTERED.code());
    attrs.put(TaxMetadataKeys.SELLER_LUT_NUMBER, "LUT123");
    attrs.put(TaxMetadataKeys.BUYER_COUNTRY, "US");
    attrs.put(
        TaxMetadataKeys.BUYER_REGISTRATION_TYPE, StandardRegistrationType.FOREIGN_ENTITY.code());

    Result<TaxComputationResult> result = calculator.calculate(contextFor(attrs));

    assertThat(result.get().breakdown().totalTax().amount()).isEqualByComparingTo("0.00");
    assertThat(componentAmount(result.get().breakdown(), StandardTaxComponentType.EXPORT))
        .isEqualByComparingTo("0.00");
  }

  @Test
  void exportUnderIgstComputesOneEighty() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put(TaxMetadataKeys.SELLER_COUNTRY, "IN");
    attrs.put(TaxMetadataKeys.SELLER_STATE, "MH");
    attrs.put(TaxMetadataKeys.SELLER_REGISTRATION_TYPE, StandardRegistrationType.EXPORTER.code());
    attrs.put(TaxMetadataKeys.BUYER_COUNTRY, "US");
    attrs.put(
        TaxMetadataKeys.BUYER_REGISTRATION_TYPE, StandardRegistrationType.FOREIGN_ENTITY.code());

    Result<TaxComputationResult> result = calculator.calculate(contextFor(attrs));

    assertThat(componentAmount(result.get().breakdown(), StandardTaxComponentType.IGST))
        .isEqualByComparingTo("180.00");
  }

  @Test
  void foreignSellerIndianBuyerComputesZero() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put(TaxMetadataKeys.SELLER_COUNTRY, "US");
    attrs.put(
        TaxMetadataKeys.SELLER_REGISTRATION_TYPE, StandardRegistrationType.FOREIGN_ENTITY.code());
    attrs.put(TaxMetadataKeys.BUYER_COUNTRY, "IN");
    attrs.put(TaxMetadataKeys.BUYER_STATE, "MH");
    attrs.put(
        TaxMetadataKeys.BUYER_REGISTRATION_TYPE, StandardRegistrationType.GST_REGISTERED.code());
    attrs.put(TaxMetadataKeys.BUYER_GST_NUMBER, "27ABCDE1234F1Z5");

    Result<TaxComputationResult> result = calculator.calculate(contextFor(attrs));

    assertThat(result.get().breakdown().totalTax().amount()).isEqualByComparingTo("0.00");
  }

  @Test
  void foreignToForeignComputesZero() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put(TaxMetadataKeys.SELLER_COUNTRY, "US");
    attrs.put(
        TaxMetadataKeys.SELLER_REGISTRATION_TYPE, StandardRegistrationType.FOREIGN_ENTITY.code());
    attrs.put(TaxMetadataKeys.BUYER_COUNTRY, "GB");
    attrs.put(
        TaxMetadataKeys.BUYER_REGISTRATION_TYPE, StandardRegistrationType.FOREIGN_ENTITY.code());

    Result<TaxComputationResult> result = calculator.calculate(contextFor(attrs));

    assertThat(result.get().breakdown().totalTax().amount()).isEqualByComparingTo("0.00");
  }

  @Test
  void missingSellerProfileReturnsFailure() {
    Map<String, String> attrs = new HashMap<>();
    attrs.put(TaxMetadataKeys.BUYER_COUNTRY, "IN");
    attrs.put(TaxMetadataKeys.BUYER_STATE, "MH");
    attrs.put(
        TaxMetadataKeys.BUYER_REGISTRATION_TYPE, StandardRegistrationType.GST_REGISTERED.code());
    attrs.put(TaxMetadataKeys.BUYER_GST_NUMBER, "27ABCDE1234F1Z5");

    Result<TaxComputationResult> result = calculator.calculate(contextFor(attrs));

    assertThat(result.isFailure()).isTrue();
  }

  @Test
  void missingRateConfigurationReturnsFailureUnsupportedTransaction() {
    TaxRateProvider emptyRateProvider = new DefaultTaxRateProvider(new RateConfiguration(Map.of()));
    IndianTaxCalculator calculatorWithNoRates =
        new IndianTaxCalculator(resolver, emptyRateProvider);

    Result<TaxComputationResult> result =
        calculatorWithNoRates.calculate(contextFor(domesticSameState()));

    assertThat(result.isFailure()).isTrue();
    result.onFailure(
        failure -> assertThat(failure.errorCode()).isEqualTo(TaxErrorCode.UNSUPPORTED_TRANSACTION));
  }

  private java.math.BigDecimal componentAmount(
      TaxBreakdown breakdown, io.genfin.tax.api.decision.TaxComponentType type) {
    return breakdown.components().stream()
        .filter(c -> c.code().equals(type.code()))
        .findFirst()
        .orElseThrow()
        .amount()
        .amount();
  }
}
