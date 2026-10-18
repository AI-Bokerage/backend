package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.qr.QrCodeContent;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class QrCodeResolversTest {

  @Test
  void registerThenResolveRoundTrips() {
    QrCodeResolvers.Bundle bundle = QrCodeResolvers.standard();
    QrCodeProvider provider =
        new QrCodeProvider() {
          @Override
          public boolean supports(String key) {
            return "payment-link".equals(key);
          }

          @Override
          public QrCodeContent resolve(String key) {
            return QrCodeContent.of("content".getBytes(StandardCharsets.UTF_8), "image/png");
          }
        };

    bundle.register(provider);

    QrCodeContent resolved = bundle.resolver().resolve("payment-link");
    assertThat(new String(resolved.imageBytes(), StandardCharsets.UTF_8)).isEqualTo("content");
  }
}
