package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.brand.BrandIdentity;
import io.genfin.document.api.brand.BrandProfile;
import io.genfin.document.api.identity.BrandId;
import org.junit.jupiter.api.Test;

class BrandResolversTest {

  @Test
  void registerThenResolveRoundTrips() {
    BrandResolvers.Bundle bundle = BrandResolvers.standard();
    BrandProfile profile =
        BrandProfile.builder(BrandId.of("brand-1"), BrandIdentity.of("Acme", "addr")).build();

    bundle.register(profile);

    assertThat(bundle.resolver().resolve(BrandId.of("brand-1"))).isSameAs(profile);
  }
}
