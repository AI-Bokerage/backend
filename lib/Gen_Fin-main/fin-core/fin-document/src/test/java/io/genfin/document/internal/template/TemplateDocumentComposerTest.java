package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.compose.ComposedDocument;
import io.genfin.document.api.compose.DocumentComposer;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentModel;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.template.DocumentTemplate;
import io.genfin.document.port.TemplateEngine;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateDocumentComposerTest {

  @Test
  void passthroughWhenNoTemplateIdAttribute() {
    TemplateEngine engine = (templateId, context) -> List.of();
    DocumentComposer composer = new TemplateDocumentComposer(engine);

    DocumentModel model =
        DocumentModel.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
            List.of(DocumentSection.of("Original", List.of(KeyValueElement.of("k", "v")))),
            DocumentAttributes.empty());

    ComposedDocument composed = composer.compose(model);

    assertThat(composed.sections()).hasSize(1);
    assertThat(composed.sections().get(0).title()).isEqualTo("Original");
  }

  @Test
  void rendersTemplateWhenTemplateIdAttributePresent() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("greeting"), "Hello {{name}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));
    DocumentComposer composer = new TemplateDocumentComposer(engine);

    DocumentModel model =
        DocumentModel.of(
            DocumentMetadata.of(
                DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH),
            List.of(DocumentSection.of("Original", List.of())),
            DocumentAttributes.of(Map.of("templateId", "greeting", "name", "World")));

    ComposedDocument composed = composer.compose(model);

    boolean containsRenderedGreeting =
        composed.sections().stream()
            .flatMap(s -> s.elements().stream())
            .anyMatch(
                e ->
                    e.accept(
                            new io.genfin.document.api.model.DocumentElementVisitor<String>() {
                              @Override
                              public String visitKeyValue(KeyValueElement element) {
                                return element.value();
                              }

                              @Override
                              public String visitTable(
                                  io.genfin.document.api.model.TableElement element) {
                                return "";
                              }

                              @Override
                              public String visitTextBlock(
                                  io.genfin.document.api.model.TextBlockElement element) {
                                return element.text();
                              }
                            })
                        .contains("World"));

    assertThat(containsRenderedGreeting).isTrue();
  }
}
