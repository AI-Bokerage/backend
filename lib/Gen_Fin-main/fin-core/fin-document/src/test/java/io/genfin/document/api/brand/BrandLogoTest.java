package io.genfin.document.api.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class BrandLogoTest {

  @Test
  void exposesGivenBytesAndMimeType() {
    byte[] source = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
    BrandLogo logo = BrandLogo.of(source, "image/png");

    assertThat(logo.imageBytes()).isEqualTo(source);
    assertThat(logo.mimeType()).isEqualTo("image/png");
  }

  @Test
  void contentIsDefensivelyCopiedOnConstructionAndReturn() {
    byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
    BrandLogo logo = BrandLogo.of(source, "image/png");

    source[0] = (byte) 0xFF;
    assertThat(logo.imageBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));

    byte[] returned = logo.imageBytes();
    returned[0] = (byte) 0xFF;
    assertThat(logo.imageBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void nullImageBytesThrowsValidationException() {
    assertThatThrownBy(() -> BrandLogo.of(null, "image/png"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullMimeTypeThrowsValidationException() {
    byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> BrandLogo.of(source, null)).isInstanceOf(ValidationException.class);
  }

  @Test
  void blankMimeTypeThrowsValidationException() {
    byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
    assertThatThrownBy(() -> BrandLogo.of(source, "")).isInstanceOf(ValidationException.class);
  }
}
