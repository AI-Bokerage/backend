package io.genfin.money.tax;

import static io.genfin.money.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.result.Result;
import io.genfin.money.money.Money;
import io.genfin.money.percentage.Percentage;
import io.genfin.money.port.tax.TaxCalculator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxModelTest {

  private static final TaxCategory STANDARD = () -> "STANDARD";

  @Test
  void noOpCalculatorReturnsZeroTaxBreakdown() {
    TaxCalculator calculator = TaxCalculators.noOp();
    TaxableAmount taxableAmount = new TaxableAmount(Money.of("100.00", USD), STANDARD);
    TaxContext context = new TaxContext(taxableAmount, TaxMetadata.empty(), Instant.EPOCH);

    Result<TaxComputationResult> result = calculator.calculate(context);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.get().breakdown().totalTax()).isEqualTo(Money.zero(USD));
  }

  @Test
  void breakdownSumsMultipleComponents() {
    TaxComponent cgst =
        new TaxComponent("CGST", Percentage.ofPercent(new BigDecimal("9")), Money.of("9.00", USD));
    TaxComponent sgst =
        new TaxComponent("SGST", Percentage.ofPercent(new BigDecimal("9")), Money.of("9.00", USD));
    TaxBreakdown breakdown = new TaxBreakdown(List.of(cgst, sgst), USD);

    assertThat(breakdown.totalTax()).isEqualTo(Money.of("18.00", USD));
  }

  @Test
  void metadataAttributesAreQueryable() {
    TaxMetadata metadata = new TaxMetadata(java.util.Map.of("placeOfSupply", "MH"));

    assertThat(metadata.find("placeOfSupply")).contains("MH");
    assertThat(metadata.find("missing")).isEmpty();
  }

  @Test
  void taxConfigurationDisabledByDefault() {
    assertThat(TaxConfiguration.disabled().taxEnabled()).isFalse();
  }
}
