package io.genfin.tax.internal.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.tax.api.config.ExportTreatment;
import io.genfin.tax.api.decision.StandardTaxTreatment;
import io.genfin.tax.api.decision.TaxDecision;
import io.genfin.tax.api.exception.InvalidTaxProfileException;
import io.genfin.tax.api.party.PartyTaxProfile;
import io.genfin.tax.api.party.StandardCountryCode;
import io.genfin.tax.api.party.StandardRegistrationType;
import io.genfin.tax.api.party.StateCode;
import io.genfin.tax.api.supply.StandardSupplyType;
import io.genfin.tax.api.transaction.TransactionContext;
import io.genfin.tax.port.TaxDecisionResolver;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Covers Rules 1-5 of Tax Engine V1's supported scope, plus the validation cases from the design.
 */
class DefaultTaxDecisionResolverTest {

  private final TransactionContext context = TransactionContext.at(Instant.now());

  private TaxDecisionResolver resolverWith(ExportTreatment defaultExportTreatment) {
    return new DefaultTaxDecisionResolver(
        new DefaultRegistrationPolicy(), new DefaultDomesticPolicy(),
        new DefaultExportPolicy(defaultExportTreatment), new DefaultImportPolicy());
  }

  private final TaxDecisionResolver resolver = resolverWith(ExportTreatment.EXPORT_WITH_LUT);

  @Test
  void rule1_sameStateIndiaResolvesToCgstSgst() {
    PartyTaxProfile seller =
        PartyTaxProfile.gstRegistered(StandardCountryCode.INDIA, StateCode.of("MH"), "S1");
    PartyTaxProfile buyer =
        PartyTaxProfile.gstRegistered(StandardCountryCode.INDIA, StateCode.of("MH"), "B1");

    TaxDecision decision = resolver.resolve(seller, buyer, context);

    assertThat(decision.treatment()).isEqualTo(StandardTaxTreatment.CGST_SGST);
    assertThat(decision.supplyType()).isEqualTo(StandardSupplyType.DOMESTIC);
  }

  @Test
  void rule2_differentStateIndiaResolvesToIgst() {
    PartyTaxProfile seller =
        PartyTaxProfile.gstRegistered(StandardCountryCode.INDIA, StateCode.of("MH"), "S1");
    PartyTaxProfile buyer =
        PartyTaxProfile.gstRegistered(StandardCountryCode.INDIA, StateCode.of("KA"), "B1");

    TaxDecision decision = resolver.resolve(seller, buyer, context);

    assertThat(decision.treatment()).isEqualTo(StandardTaxTreatment.IGST);
    assertThat(decision.supplyType()).isEqualTo(StandardSupplyType.INTERSTATE);
  }

  @Test
  void rule3_lutRegisteredSellerExportingResolvesToExportLutRegardlessOfDefault() {
    PartyTaxProfile seller =
        PartyTaxProfile.lutRegistered(StandardCountryCode.INDIA, StateCode.of("MH"), "LUT1");
    PartyTaxProfile buyer = PartyTaxProfile.foreign(StandardCountryCode.of("US"));

    TaxDecision decision =
        resolverWith(ExportTreatment.EXPORT_WITH_IGST).resolve(seller, buyer, context);

    assertThat(decision.treatment()).isEqualTo(StandardTaxTreatment.EXPORT_LUT);
    assertThat(decision.supplyType()).isEqualTo(StandardSupplyType.EXPORT);
  }

  @Test
  void rule3_exporterWithoutLutResolvesToExportIgstRegardlessOfDefault() {
    PartyTaxProfile seller =
        PartyTaxProfile.of(
            StandardCountryCode.INDIA, StateCode.of("MH"), StandardRegistrationType.EXPORTER);
    PartyTaxProfile buyer = PartyTaxProfile.foreign(StandardCountryCode.of("US"));

    TaxDecision decision =
        resolverWith(ExportTreatment.EXPORT_WITH_LUT).resolve(seller, buyer, context);

    assertThat(decision.treatment()).isEqualTo(StandardTaxTreatment.EXPORT_IGST);
  }

  @Test
  void rule3_neitherLutNorExporterFallsBackToConfiguredDefault() {
    PartyTaxProfile seller =
        PartyTaxProfile.gstRegistered(StandardCountryCode.INDIA, StateCode.of("MH"), "S1");
    PartyTaxProfile buyer = PartyTaxProfile.foreign(StandardCountryCode.of("US"));

    assertThat(
            resolverWith(ExportTreatment.EXPORT_WITH_LUT)
                .resolve(seller, buyer, context)
                .treatment())
        .isEqualTo(StandardTaxTreatment.EXPORT_LUT);
    assertThat(
            resolverWith(ExportTreatment.EXPORT_WITH_IGST)
                .resolve(seller, buyer, context)
                .treatment())
        .isEqualTo(StandardTaxTreatment.EXPORT_IGST);
  }

  @Test
  void rule4_foreignSellerIndianBuyerResolvesToImportService() {
    PartyTaxProfile seller = PartyTaxProfile.foreign(StandardCountryCode.of("US"));
    PartyTaxProfile buyer =
        PartyTaxProfile.gstRegistered(StandardCountryCode.INDIA, StateCode.of("MH"), "B1");

    TaxDecision decision = resolver.resolve(seller, buyer, context);

    assertThat(decision.treatment()).isEqualTo(StandardTaxTreatment.IMPORT_SERVICE);
    assertThat(decision.supplyType()).isEqualTo(StandardSupplyType.IMPORT);
  }

  @Test
  void rule5_foreignToForeignResolvesToNoTax() {
    PartyTaxProfile seller = PartyTaxProfile.foreign(StandardCountryCode.of("US"));
    PartyTaxProfile buyer = PartyTaxProfile.foreign(StandardCountryCode.of("GB"));

    TaxDecision decision = resolver.resolve(seller, buyer, context);

    assertThat(decision.treatment()).isEqualTo(StandardTaxTreatment.NO_TAX);
    assertThat(decision.supplyType()).isEqualTo(StandardSupplyType.NONE);
  }

  @Test
  void gstRegisteredIndianPartyMissingStateFailsValidation() {
    // Constructing via the raw constructor (not the .gstRegistered() factory) to reach a
    // GST_REGISTERED profile with a null state without PartyTaxProfile's own constructor
    // rejecting it first — this is exactly the shape IndianTaxCalculator can receive from
    // partially-populated TaxMetadata.
    PartyTaxProfile sellerMissingState =
        new PartyTaxProfile(
            StandardCountryCode.INDIA, null, StandardRegistrationType.GST_REGISTERED, "S1", null);
    PartyTaxProfile buyer =
        PartyTaxProfile.gstRegistered(StandardCountryCode.INDIA, StateCode.of("MH"), "B1");

    assertThatThrownBy(() -> resolver.resolve(sellerMissingState, buyer, context))
        .isInstanceOf(InvalidTaxProfileException.class);
  }

  @Test
  void unknownRegistrationTypeDoesNotBreakResolution() {
    PartyTaxProfile seller =
        PartyTaxProfile.of(
            StandardCountryCode.INDIA,
            StateCode.of("MH"),
            io.genfin.tax.api.party.StandardRegistrationType.of("SOME_FUTURE_TYPE"));
    PartyTaxProfile buyer =
        PartyTaxProfile.gstRegistered(StandardCountryCode.INDIA, StateCode.of("MH"), "B1");

    TaxDecision decision = resolver.resolve(seller, buyer, context);

    assertThat(decision.treatment()).isEqualTo(StandardTaxTreatment.CGST_SGST);
  }
}
