package io.genfin.document.spi;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.spi.ExtensionRegistries;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.document.port.BrandResolver;
import io.genfin.document.port.DocumentEventPublisher;
import io.genfin.document.port.DocumentValidator;
import io.genfin.document.port.LetterheadResolver;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.PreviewRenderer;
import io.genfin.document.port.QrCodeResolver;
import io.genfin.document.port.RendererResolver;
import io.genfin.document.port.TemplateEngine;
import org.junit.jupiter.api.Test;

class DocumentExtensionsTest {

  @Test
  void allDefaultDocumentExtensionsAreDiscoverableAfterRegistration() {
    ExtensionRegistry registry = ExtensionRegistries.create();

    DocumentExtensions.registerDefaults(registry);

    assertThat(registry.find(RendererResolver.class)).isPresent();
    assertThat(registry.find(BrandResolver.class)).isPresent();
    assertThat(registry.find(LetterheadResolver.class)).isPresent();
    assertThat(registry.find(QrCodeResolver.class)).isPresent();
    assertThat(registry.find(TemplateEngine.class)).isPresent();
    assertThat(registry.find(PlaceholderResolver.class)).isPresent();
    assertThat(registry.find(DocumentEventPublisher.class)).isPresent();
    assertThat(registry.find(PreviewRenderer.class)).isPresent();
    assertThat(registry.find(DocumentValidator.class)).isPresent();
  }
}
