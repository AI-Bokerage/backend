package io.genfin.document.api.compose;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentModel;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.StandardDocumentType;
import io.genfin.document.api.model.TextBlockElement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentComposersTest {

  @Test
  void noOpComposerCopiesModelUnchanged() {
    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en-IN", "INR", Instant.EPOCH);
    DocumentSection section =
        DocumentSection.of("Summary", List.of(KeyValueElement.of("Total", "500.00")));
    DocumentModel model =
        DocumentModel.of(
            metadata, List.of(section), DocumentAttributes.of(java.util.Map.of("region", "IN")));

    DocumentComposer composer = DocumentComposers.noOp();
    ComposedDocument composed = composer.compose(model);

    assertThat(composed.metadata().id()).isEqualTo(DocumentId.of("doc-1"));
    assertThat(composed.sections()).hasSize(1);
    assertThat(composed.sections().get(0).title()).isEqualTo("Summary");
    assertThat(composed.attributes().get("region")).contains("IN");
  }

  @Test
  void templatedComposerDelegatesToTheGivenTemplateEngine() {
    DocumentSection rendered =
        DocumentSection.of("rendered", List.of(TextBlockElement.of("Hello World")));
    io.genfin.document.port.TemplateEngine stubEngine = (templateId, context) -> List.of(rendered);

    DocumentComposer composer = DocumentComposers.templated(stubEngine);

    DocumentMetadata metadata =
        DocumentMetadata.of(
            DocumentId.of("doc-1"), StandardDocumentType.INVOICE, "en", "USD", Instant.EPOCH);
    DocumentModel model =
        DocumentModel.of(
            metadata,
            List.of(DocumentSection.of("Original", List.of())),
            DocumentAttributes.of(Map.of("templateId", "greeting")));

    ComposedDocument composed = composer.compose(model);

    assertThat(composed.sections()).hasSize(1);
    assertThat(composed.sections().get(0).title()).isEqualTo("rendered");
  }
}
