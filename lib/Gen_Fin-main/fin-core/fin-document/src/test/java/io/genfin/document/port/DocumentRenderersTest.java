package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.RendererId;
import org.junit.jupiter.api.Test;

class DocumentRenderersTest {

  @Test
  void standardResolverHasAllFiveBuiltInRenderers() {
    RendererResolver resolver = DocumentRenderers.standard();

    assertThat(resolver.resolve(RendererId.of("json")).capabilities().mimeType())
        .isEqualTo("application/json");
    assertThat(resolver.resolve(RendererId.of("text")).capabilities().mimeType())
        .isEqualTo("text/plain");
    assertThat(resolver.resolve(RendererId.of("markdown")).capabilities().mimeType())
        .isEqualTo("text/markdown");
    assertThat(resolver.resolve(RendererId.of("csv")).capabilities().mimeType())
        .isEqualTo("text/csv");
    assertThat(resolver.resolve(RendererId.of("xml")).capabilities().mimeType())
        .isEqualTo("application/xml");
    assertThat(resolver.resolve(RendererId.of("pdf")).capabilities().mimeType())
        .isEqualTo("application/pdf");
  }
}
