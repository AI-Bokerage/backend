package io.genfin.document.internal.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.brand.BrandIdentity;
import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.exception.BrandNotFoundException;
import io.genfin.document.api.identity.BrandId;
import org.junit.jupiter.api.Test;

class DefaultBrandResolverTest {

  @Test
  void resolvesRegisteredBrand() {
    DefaultBrandRegistry registry = new DefaultBrandRegistry();
    BrandProfile profile =
        BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme", "addr")).build();
    registry.register(profile);
    DefaultBrandResolver resolver = new DefaultBrandResolver(registry);

    assertThat(resolver.resolve(BrandId.of("brand-1"))).isSameAs(profile);
  }

  @Test
  void throwsForUnregisteredBrand() {
    DefaultBrandResolver resolver = new DefaultBrandResolver(new DefaultBrandRegistry());

    assertThatThrownBy(() -> resolver.resolve(BrandId.of("missing")))
        .isInstanceOf(BrandNotFoundException.class);
  }

  @Test
  void rejectsDuplicateRegistration() {
    DefaultBrandRegistry registry = new DefaultBrandRegistry();
    registry.register(
        BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme", "addr")).build());

    assertThatThrownBy(
            () ->
                registry.register(
                    BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Other", "addr"))
                        .build()))
        .isInstanceOf(IllegalStateException.class);
  }
}
