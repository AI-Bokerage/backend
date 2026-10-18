package io.genfin.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.RendererId;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.api.result.RenderResult;
import io.genfin.document.api.template.DocumentTemplate;
import io.genfin.document.api.template.TemplateContext;
import io.genfin.document.port.DocumentRenderer;
import io.genfin.document.port.DocumentRenderers;
import io.genfin.document.port.PlaceholderProvider;
import io.genfin.document.port.PlaceholderResolvers;
import io.genfin.document.port.RendererConfiguration;
import io.genfin.document.port.RendererResolver;
import io.genfin.document.port.TemplateEngines;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Seam-correctness round trip called for by the Stage 2 design spec's Testing section: render a
 * template, wrap the resulting {@link DocumentSection}s into a {@link ComposedDocument}, and feed
 * that into a Stage 1 renderer, asserting the substituted placeholder value survives all the way
 * into the final rendered bytes.
 */
class TemplateEngineRendererIntegrationTest {

  @Test
  void templateRenderedSectionsSurviveThroughToFinalJsonOutput() throws Exception {
    TemplateEngines.Bundle templates = TemplateEngines.standard();
    templates.register(
        DocumentTemplate.of(TemplateId.of("invoice-summary"), "Total due: {{amount}}"));

    PlaceholderResolvers.Bundle placeholders = PlaceholderResolvers.standard();
    placeholders.register(
        new PlaceholderProvider() {
          @Override
          public boolean supports(String key) {
            return "amount".equals(key);
          }

          @Override
          public io.genfin.document.api.placeholder.PlaceholderValue resolve(String key) {
            return ScalarPlaceholderValue.of("1234.56");
          }
        });

    List<DocumentSection> renderedSections =
        templates
            .engine()
            .render(TemplateId.of("invoice-summary"), TemplateContext.of(placeholders.resolver()));

    ComposedDocument composed =
        ComposedDocument.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"),
                StandardDocumentType.INVOICE,
                "en-IN",
                "INR",
                Instant.EPOCH),
            renderedSections,
            DocumentAttributes.empty());

    RendererResolver renderers = DocumentRenderers.standard();
    DocumentRenderer json = renderers.resolve(RendererId.of("json"));
    RenderResult result = json.render(composed, RendererConfiguration.defaults());

    String output = new String(result.content(), StandardCharsets.UTF_8);
    assertThat(output).contains("Total due: ").contains("1234.56");
  }
}
