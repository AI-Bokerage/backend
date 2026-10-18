package io.genfin.document.internal.qr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.exception.QrCodeNotFoundException;
import io.genfin.document.api.qr.QrCodeContent;
import io.genfin.document.port.QrCodeProvider;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DefaultQrCodeResolverTest {

  private static QrCodeProvider providerFor(String supportedKey, String content) {
    return new QrCodeProvider() {
      @Override
      public boolean supports(String key) {
        return supportedKey.equals(key);
      }

      @Override
      public QrCodeContent resolve(String key) {
        return QrCodeContent.of(content.getBytes(StandardCharsets.UTF_8), "image/png");
      }
    };
  }

  @Test
  void resolvesThroughFirstSupportingProviderAmongMultiple() {
    DefaultQrCodeRegistry registry = new DefaultQrCodeRegistry();
    registry.register(providerFor("other-key", "other-content"));
    registry.register(providerFor("payment-link", "payment-content"));
    DefaultQrCodeResolver resolver = new DefaultQrCodeResolver(registry);

    QrCodeContent resolved = resolver.resolve("payment-link");

    assertThat(new String(resolved.imageBytes(), StandardCharsets.UTF_8))
        .isEqualTo("payment-content");
  }

  @Test
  void firstRegisteredSupportingProviderWinsOverLaterOnes() {
    DefaultQrCodeRegistry registry = new DefaultQrCodeRegistry();
    registry.register(providerFor("payment-link", "first-content"));
    registry.register(providerFor("payment-link", "second-content"));
    DefaultQrCodeResolver resolver = new DefaultQrCodeResolver(registry);

    QrCodeContent resolved = resolver.resolve("payment-link");

    assertThat(new String(resolved.imageBytes(), StandardCharsets.UTF_8))
        .isEqualTo("first-content");
  }

  @Test
  void throwsForUnsupportedKey() {
    DefaultQrCodeResolver resolver = new DefaultQrCodeResolver(new DefaultQrCodeRegistry());

    assertThatThrownBy(() -> resolver.resolve("missing"))
        .isInstanceOf(QrCodeNotFoundException.class);
  }
}
