package io.genfin.providerapi.capability;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.payment.method.StandardPaymentMethodType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProviderFeatureMatrixTest {

  private final ProviderFeatureMatrix matrix =
      new ProviderFeatureMatrix(
          Set.of(CaptureMode.AUTOMATIC),
          Set.of(StandardPaymentMethodType.CARD.code()),
          Set.of("usd", "eur"));

  @Test
  void supportsCurrencyIsCaseInsensitive() {
    assertThat(matrix.supportsCurrency("USD")).isTrue();
    assertThat(matrix.supportsCurrency("usd")).isTrue();
    assertThat(matrix.supportsCurrency("INR")).isFalse();
  }

  @Test
  void supportsCaptureModeRequiresBothCaptureModeAndMethodToMatch() {
    assertThat(matrix.supportsCaptureMode(CaptureMode.AUTOMATIC, StandardPaymentMethodType.CARD))
        .isTrue();
    assertThat(matrix.supportsCaptureMode(CaptureMode.MANUAL, StandardPaymentMethodType.CARD))
        .isFalse();
    assertThat(matrix.supportsCaptureMode(CaptureMode.AUTOMATIC, StandardPaymentMethodType.UPI))
        .isFalse();
  }
}
