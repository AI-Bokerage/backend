package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrandAssetsTest {

  @Test
  void emptyHasNoLogo() {
    assertThat(BrandAssets.empty().logo()).isEmpty();
  }

  @Test
  void ofExposesGivenLogo() {
    BrandLogo logo = BrandLogo.of("x".getBytes(StandardCharsets.UTF_8), "image/png");
    assertThat(BrandAssets.of(logo).logo()).contains(logo);
  }
}
