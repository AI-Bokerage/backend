package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrandRegistrationInformationTest {

  @Test
  void exposesGivenValues() {
    BrandRegistrationInformation info = BrandRegistrationInformation.of("TAX-1", "BIZ-1");
    assertThat(info.taxRegistrationNumber()).isEqualTo("TAX-1");
    assertThat(info.businessRegistrationNumber()).isEqualTo("BIZ-1");
  }

  @Test
  void nullInputsBecomeEmptyStrings() {
    BrandRegistrationInformation info = BrandRegistrationInformation.of(null, null);
    assertThat(info.taxRegistrationNumber()).isEmpty();
    assertThat(info.businessRegistrationNumber()).isEmpty();
  }
}
