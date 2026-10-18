package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.BrandId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrandProfileTest {

  @Test
  void buildsWithOnlyRequiredFieldsAndDefaultsTheRest() {
    BrandProfile profile =
        BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme Inc.", "123 Main St"))
            .build();

    assertThat(profile.id()).isEqualTo(BrandId.of("brand-1"));
    assertThat(profile.identity().companyName()).isEqualTo("Acme Inc.");
    assertThat(profile.assets().logo()).isEmpty();
    assertThat(profile.contact().phone()).isEmpty();
    assertThat(profile.registration().taxRegistrationNumber()).isEmpty();
    assertThat(profile.header().text()).isEmpty();
    assertThat(profile.footer().text()).isEmpty();
  }

  @Test
  void buildsWithAllFieldsSet() {
    BrandLogo logo = BrandLogo.of("x".getBytes(StandardCharsets.UTF_8), "image/png");
    BrandProfile profile =
        BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme Inc.", "123 Main St"))
            .assets(BrandAssets.of(logo))
            .contact(BrandContactInformation.of("+1-555-0100", "hi@acme.test", "acme.test"))
            .registration(BrandRegistrationInformation.of("TAX-1", "BIZ-1"))
            .header(BrandHeader.of("Trusted since 1990"))
            .footer(BrandFooter.of("All rights reserved"))
            .build();

    assertThat(profile.assets().logo()).contains(logo);
    assertThat(profile.contact().phone()).isEqualTo("+1-555-0100");
    assertThat(profile.registration().taxRegistrationNumber()).isEqualTo("TAX-1");
    assertThat(profile.header().text()).isEqualTo("Trusted since 1990");
    assertThat(profile.footer().text()).isEqualTo("All rights reserved");
  }

  @Test
  void nullIdThrowsValidationException() {
    assertThatThrownBy(() -> BrandProfile.builder(null, BrandIdentity.of("Acme", "addr")).build())
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullIdentityThrowsValidationException() {
    assertThatThrownBy(() -> BrandProfile.builder(BrandId.of("brand-1"), null).build())
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullOptionalFieldsCoalesceToEmptyDefaultsRatherThanNull() {
    BrandProfile profile =
        BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme Inc.", "123 Main St"))
            .assets(null)
            .contact(null)
            .registration(null)
            .header(null)
            .footer(null)
            .build();

    assertThat(profile.assets()).isSameAs(BrandAssets.empty());
    assertThat(profile.assets().logo()).isEmpty();
    assertThat(profile.contact().phone()).isEmpty();
    assertThat(profile.contact().email()).isEmpty();
    assertThat(profile.contact().website()).isEmpty();
    assertThat(profile.registration().taxRegistrationNumber()).isEmpty();
    assertThat(profile.registration().businessRegistrationNumber()).isEmpty();
    assertThat(profile.header().text()).isEmpty();
    assertThat(profile.footer().text()).isEmpty();
  }
}
