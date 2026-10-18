package io.genfin.autoconfigure.document;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.autoconfigure.GenFinExtensionRegistryAutoConfiguration;
import io.genfin.document.api.exception.RendererNotFoundException;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.DocumentRenderers;
import io.genfin.document.port.LetterheadProvider;
import io.genfin.document.port.LetterheadResolver;
import io.genfin.document.port.LetterheadResolvers;
import io.genfin.document.port.PlaceholderProvider;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.PlaceholderResolvers;
import io.genfin.document.port.RendererResolver;
import io.genfin.document.spi.DocumentExtensions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers every default fin-document extension into the shared {@link ExtensionRegistry}.
 * Application-supplied {@link PlaceholderProvider}/{@link LetterheadProvider}/{@link
 * DocumentRenderer} beans are folded in before fin-document's own defaults, so they take priority.
 */
@AutoConfiguration
@AutoConfigureAfter(GenFinExtensionRegistryAutoConfiguration.class)
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "documentExtensionsRegistered")
  public Boolean documentExtensionsRegistered(
      ExtensionRegistry registry,
      DocumentProperties properties,
      ObjectProvider<List<PlaceholderProvider>> placeholderProviders,
      ObjectProvider<List<LetterheadProvider>> letterheadProviders,
      ObjectProvider<List<DocumentRenderer>> customRenderers) {
    registerPlaceholderProviders(registry, placeholderProviders.getIfAvailable(List::of));
    registerLetterheadProviders(registry, letterheadProviders.getIfAvailable(List::of));
    registerCustomRenderers(registry, customRenderers.getIfAvailable(List::of));

    DocumentExtensions.registerDefaults(registry);

    validateDefaultRenderer(registry, properties);
    return Boolean.TRUE;
  }

  private void registerPlaceholderProviders(
      ExtensionRegistry registry, List<PlaceholderProvider> providers) {
    if (providers.isEmpty()) {
      return;
    }
    PlaceholderResolvers.Bundle bundle = PlaceholderResolvers.standard();
    providers.forEach(bundle::register);
    registry.register(PlaceholderResolver.class, bundle.resolver());
  }

  private void registerLetterheadProviders(
      ExtensionRegistry registry, List<LetterheadProvider> providers) {
    if (providers.isEmpty()) {
      return;
    }
    LetterheadResolvers.Bundle bundle = LetterheadResolvers.standard();
    providers.forEach(bundle::register);
    registry.register(LetterheadResolver.class, bundle.resolver());
  }

  private void registerCustomRenderers(
      ExtensionRegistry registry, List<DocumentRenderer> renderers) {
    if (renderers.isEmpty()) {
      return;
    }
    Map<RendererId, DocumentRenderer> byId = new LinkedHashMap<>();
    for (DocumentRenderer renderer : renderers) {
      DocumentRenderer previous = byId.put(renderer.id(), renderer);
      if (previous != null) {
        throw new IllegalStateException(
            "Duplicate DocumentRenderer id registered as a Spring bean: " + renderer.id());
      }
    }
    registry.register(
        RendererResolver.class,
        new SpringAwareRendererResolver(byId, DocumentRenderers.standard()));
  }

  private void validateDefaultRenderer(ExtensionRegistry registry, DocumentProperties properties) {
    RendererResolver resolver =
        registry.find(RendererResolver.class).orElseThrow(IllegalStateException::new);
    RendererId defaultRenderer = RendererId.of(properties.getRenderer());
    try {
      resolver.resolve(defaultRenderer);
    } catch (RendererNotFoundException e) {
      throw new IllegalStateException(
          "genfin.document.renderer='"
              + properties.getRenderer()
              + "' does not match any registered renderer",
          e);
    }
  }
}
