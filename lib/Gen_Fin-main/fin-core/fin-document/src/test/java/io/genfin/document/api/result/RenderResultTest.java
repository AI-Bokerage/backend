package io.genfin.document.api.result;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.RendererId;
import org.junit.jupiter.api.Test;

class RenderResultTest {

  @Test
  void ofExposesRendererIdContentAndMimeType() {
    RenderResult result =
        RenderResult.of(
            RendererId.of("json"),
            "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "application/json");

    assertThat(result.rendererId()).isEqualTo(RendererId.of("json"));
    assertThat(new String(result.content(), java.nio.charset.StandardCharsets.UTF_8))
        .isEqualTo("{}");
    assertThat(result.mimeType()).isEqualTo("application/json");
  }

  @Test
  void contentIsDefensivelyCopiedOnConstructionAndReturn() {
    byte[] sourceBytes = new byte[] {0x01, 0x02, 0x03};
    RenderResult result =
        RenderResult.of(RendererId.of("test"), sourceBytes, "application/octet-stream");

    // Mutate source array after passing to RenderResult
    sourceBytes[0] = (byte) 0xFF;

    // Verify content() returns original unmutated bytes (constructor copied)
    byte[] retrievedBytes = result.content();
    assertThat(retrievedBytes).isEqualTo(new byte[] {0x01, 0x02, 0x03});

    // Mutate the retrieved array
    retrievedBytes[0] = (byte) 0xFF;

    // Verify second call to content() returns original unmutated bytes (accessor copied)
    assertThat(result.content()).isEqualTo(new byte[] {0x01, 0x02, 0x03});
  }
}
