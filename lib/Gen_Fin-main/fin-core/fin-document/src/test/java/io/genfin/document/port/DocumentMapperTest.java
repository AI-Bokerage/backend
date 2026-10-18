package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.model.DocumentAttributes;
import io.genfin.document.api.model.DocumentMetadata;
import io.genfin.document.api.model.DocumentModel;
import io.genfin.document.api.model.StandardDocumentType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentMapperTest {

  private record TestInvoice(String id, String total) {}

  @Test
  void mapperContractProducesDocumentModelFromArbitrarySource() {
    DocumentMapper<TestInvoice> mapper =
        invoice ->
            DocumentModel.of(
                DocumentMetadata.of(
                    DocumentId.of(invoice.id()),
                    StandardDocumentType.INVOICE,
                    "en-IN",
                    "INR",
                    Instant.EPOCH),
                List.of(),
                DocumentAttributes.of(java.util.Map.of("total", invoice.total())));

    DocumentModel model = mapper.map(new TestInvoice("inv-1", "500.00"));

    assertThat(model.metadata().id()).isEqualTo(DocumentId.of("inv-1"));
    assertThat(model.attributes().get("total")).contains("500.00");
  }
}
