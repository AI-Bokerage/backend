package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentElementTest {

  private static final class RecordingVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return "kv:" + element.label() + "=" + element.value();
    }

    @Override
    public String visitTable(TableElement element) {
      return "table:" + element.headers().size() + "x" + element.rows().size();
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return "text:" + element.text();
    }
  }

  @Test
  void keyValueElementDispatchesToVisitKeyValue() {
    DocumentElement element = KeyValueElement.of("Total", "100.00");
    assertThat(element.accept(new RecordingVisitor())).isEqualTo("kv:Total=100.00");
  }

  @Test
  void tableElementDispatchesToVisitTable() {
    DocumentElement element =
        TableElement.of(List.of("Item", "Qty"), List.of(List.of("Widget", "2")));
    assertThat(element.accept(new RecordingVisitor())).isEqualTo("table:2x1");
  }

  @Test
  void textBlockElementDispatchesToVisitTextBlock() {
    DocumentElement element = TextBlockElement.of("Thank you for your business.");
    assertThat(element.accept(new RecordingVisitor()))
        .isEqualTo("text:Thank you for your business.");
  }

  @Test
  void tableElementOfRejectsNullHeaders() {
    assertThatThrownBy(() -> TableElement.of(null, List.of()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void tableElementOfRejectsNullRows() {
    assertThatThrownBy(() -> TableElement.of(List.of("Item"), null))
        .isInstanceOf(ValidationException.class);
  }
}
