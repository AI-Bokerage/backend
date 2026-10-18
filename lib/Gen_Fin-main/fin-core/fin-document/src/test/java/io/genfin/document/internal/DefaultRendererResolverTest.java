package io.genfin.document.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.exception.RendererNotFoundException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.RendererCapabilities;
import io.genfin.document.port.RendererConfiguration;
import org.junit.jupiter.api.Test;

class DefaultRendererResolverTest {

  private static final class StubRenderer implements DocumentRenderer {
    @Override
    public RendererId id() {
      return RendererId.of("stub");
    }

    @Override
    public RendererCapabilities capabilities() {
      return () -> "text/plain";
    }

    @Override
    public RenderResult render(ComposedDocument document, RendererConfiguration configuration) {
      return RenderResult.of(id(), new byte[0], capabilities().mimeType());
    }
  }

  @Test
  void resolvesRegisteredRenderer() {
    DefaultRendererRegistry registry = new DefaultRendererRegistry();
    StubRenderer renderer = new StubRenderer();
    registry.register(renderer);
    DefaultRendererResolver resolver = new DefaultRendererResolver(registry);

    assertThat(resolver.resolve(RendererId.of("stub"))).isSameAs(renderer);
  }

  @Test
  void throwsForUnregisteredRenderer() {
    DefaultRendererResolver resolver = new DefaultRendererResolver(new DefaultRendererRegistry());

    assertThatThrownBy(() -> resolver.resolve(RendererId.of("missing")))
        .isInstanceOf(RendererNotFoundException.class);
  }

  @Test
  void rejectsDuplicateRegistration() {
    DefaultRendererRegistry registry = new DefaultRendererRegistry();
    registry.register(new StubRenderer());

    assertThatThrownBy(() -> registry.register(new StubRenderer()))
        .isInstanceOf(IllegalStateException.class);
  }
}
