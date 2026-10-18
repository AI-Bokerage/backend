package io.genfin.document.api.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QrCodeContentTest {

  @Test
  void exposesGivenBytesAndMimeType() {
    byte[] source = "fake-qr-png".getBytes(StandardCharsets.UTF_8);
    QrCodeContent content = QrCodeContent.of(source, "image/png");

    assertThat(content.imageBytes()).isEqualTo(source);
    assertThat(content.mimeType()).isEqualTo("image/png");
  }

  @Test
  void contentIsDefensivelyCopiedOnConstructionAndReturn() {
    byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
    QrCodeContent content = QrCodeContent.of(source, "image/png");

    source[0] = (byte) 0xFF;
    assertThat(content.imageBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));

    byte[] returned = content.imageBytes();
    returned[0] = (byte) 0xFF;
    assertThat(content.imageBytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void nullImageBytesThrowsValidationException() {
    assertThatThrownBy(() -> QrCodeContent.of(null, "image/png"))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }

  @Test
  void blankMimeTypeThrowsValidationException() {
    assertThatThrownBy(() -> QrCodeContent.of("x".getBytes(StandardCharsets.UTF_8), ""))
        .isInstanceOf(io.genfin.api.exception.ValidationException.class);
  }
}
