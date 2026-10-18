package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrandContactInformationTest {

  @Test
  void exposesGivenValues() {
    BrandContactInformation info =
        BrandContactInformation.of("+1-555-0100", "hi@acme.test", "acme.test");
    assertThat(info.phone()).isEqualTo("+1-555-0100");
    assertThat(info.email()).isEqualTo("hi@acme.test");
    assertThat(info.website()).isEqualTo("acme.test");
  }

  @Test
  void nullInputsBecomeEmptyStrings() {
    BrandContactInformation info = BrandContactInformation.of(null, null, null);
    assertThat(info.phone()).isEmpty();
    assertThat(info.email()).isEmpty();
    assertThat(info.website()).isEmpty();
  }
}
