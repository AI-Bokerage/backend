package io.genfin.tax.api.party;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.tax.api.exception.InvalidTaxProfileException;
import org.junit.jupiter.api.Test;

class PartyTaxProfileTest {

  @Test
  void gstRegisteredWithoutGstNumberThrows() {
    assertThatThrownBy(
            () ->
                PartyTaxProfile.of(
                    StandardCountryCode.INDIA,
                    StateCode.of("MH"),
                    StandardRegistrationType.GST_REGISTERED))
        .isInstanceOf(InvalidTaxProfileException.class)
        .hasMessageContaining("gstNumber");
  }

  @Test
  void lutRegisteredWithoutLutNumberThrows() {
    assertThatThrownBy(
            () ->
                PartyTaxProfile.of(
                    StandardCountryCode.INDIA,
                    StateCode.of("MH"),
                    StandardRegistrationType.LUT_REGISTERED))
        .isInstanceOf(InvalidTaxProfileException.class)
        .hasMessageContaining("lutNumber");
  }

  @Test
  void gstRegisteredWithGstNumberConstructsSuccessfully() {
    PartyTaxProfile profile =
        PartyTaxProfile.gstRegistered(
            StandardCountryCode.INDIA, StateCode.of("MH"), "27ABCDE1234F1Z5");

    assertThat(profile.gstNumberOptional()).contains("27ABCDE1234F1Z5");
    assertThat(profile.stateOptional()).contains(StateCode.of("MH"));
  }

  @Test
  void foreignPartyHasNoStateOrRegistrationNumbers() {
    PartyTaxProfile profile = PartyTaxProfile.foreign(StandardCountryCode.of("US"));

    assertThat(profile.stateOptional()).isEmpty();
    assertThat(profile.gstNumberOptional()).isEmpty();
    assertThat(profile.lutNumberOptional()).isEmpty();
    assertThat(profile.registrationType()).isEqualTo(StandardRegistrationType.FOREIGN_ENTITY);
  }
}
