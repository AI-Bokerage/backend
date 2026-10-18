package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class BrandIdentityTest {

  @Test
  void exposesGivenValues() {
    BrandIdentity identity = BrandIdentity.of("Acme Inc.", "123 Main St");
    assertThat(identity.companyName()).isEqualTo("Acme Inc.");
    assertThat(identity.addressLine()).isEqualTo("123 Main St");
  }

  @Test
  void nullAddressLineBecomesEmptyString() {
    BrandIdentity identity = BrandIdentity.of("Acme Inc.", null);
    assertThat(identity.addressLine()).isEmpty();
  }

  @Test
  void blankCompanyNameThrowsValidationException() {
    assertThatThrownBy(() -> BrandIdentity.of(" ", "addr")).isInstanceOf(ValidationException.class);
  }

  @Test
  void nullCompanyNameThrowsValidationException() {
    assertThatThrownBy(() -> BrandIdentity.of(null, "addr"))
        .isInstanceOf(ValidationException.class);
  }
}
