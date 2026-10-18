package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.event.DocumentEvent;
import io.genfin.document.api.event.DocumentRendered;
import io.genfin.document.api.identity.DocumentId;
import io.genfin.document.api.identity.RendererId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentEventPublishersTest {

  @Test
  void registeredListenerReceivesPublishedEvent() {
    DocumentEventPublishers.Bundle bundle = DocumentEventPublishers.standard();
    List<DocumentEvent> received = new ArrayList<>();
    bundle.register(received::add);

    DocumentRendered event = DocumentRendered.of(RendererId.of("pdf"), DocumentId.of("doc-1"));
    bundle.publisher().publish(event);

    assertThat(received).containsExactly(event);
  }

  @Test
  void multipleListenersAllReceiveEventInRegistrationOrder() {
    DocumentEventPublishers.Bundle bundle = DocumentEventPublishers.standard();
    List<String> order = new ArrayList<>();
    bundle.register(event -> order.add("first"));
    bundle.register(event -> order.add("second"));

    bundle.publisher().publish(DocumentRendered.of(RendererId.of("pdf"), DocumentId.of("doc-1")));

    assertThat(order).containsExactly("first", "second");
  }
}
