package io.genfin.document.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.document.port.BrandResolver;
import io.genfin.document.port.BrandResolvers;
import io.genfin.document.port.DocumentEventPublisher;
import io.genfin.document.port.DocumentEventPublishers;
import io.genfin.document.port.DocumentRenderers;
import io.genfin.document.port.DocumentValidator;
import io.genfin.document.port.DocumentValidators;
import io.genfin.document.port.LetterheadResolver;
import io.genfin.document.port.LetterheadResolvers;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.PlaceholderResolvers;
import io.genfin.document.port.PreviewRenderer;
import io.genfin.document.port.PreviewRenderers;
import io.genfin.document.port.QrCodeResolver;
import io.genfin.document.port.QrCodeResolvers;
import io.genfin.document.port.RendererResolver;
import io.genfin.document.port.TemplateEngine;
import io.genfin.document.port.TemplateEngines;

/**
 * Registers every default fin-document extension so downstream code discovers them through one
 * mechanism. Mirrors {@code io.genfin.pricing.spi.PricingExtensions}.
 *
 * <p>fin-document ships no built-in brand profiles, letterhead providers, QR providers or
 * placeholder sources, so {@link BrandResolver}, {@link LetterheadResolver}, {@link QrCodeResolver}
 * and {@link PlaceholderResolver} are registered empty via their standard bundles. An application
 * that wants its own provider to win must register its own resolver under the same port type
 * <b>before</b> calling {@link #registerDefaults}, since {@link ExtensionRegistry#find} returns the
 * first-registered implementation.
 */
public final class DocumentExtensions {

  private DocumentExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    RendererResolver rendererResolver = DocumentRenderers.standard();
    registry.register(RendererResolver.class, rendererResolver);

    registry.register(BrandResolver.class, BrandResolvers.standard().resolver());
    registry.register(LetterheadResolver.class, LetterheadResolvers.standard().resolver());
    registry.register(QrCodeResolver.class, QrCodeResolvers.standard().resolver());
    registry.register(TemplateEngine.class, TemplateEngines.standard().engine());
    registry.register(PlaceholderResolver.class, PlaceholderResolvers.standard().resolver());
    registry.register(DocumentEventPublisher.class, DocumentEventPublishers.standard().publisher());

    registry.register(PreviewRenderer.class, PreviewRenderers.of(rendererResolver));
    registry.register(DocumentValidator.class, DocumentValidators.of(rendererResolver));
  }
}
