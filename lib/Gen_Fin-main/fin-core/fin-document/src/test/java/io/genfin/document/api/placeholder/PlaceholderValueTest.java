package io.genfin.document.api.placeholder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceholderValueTest {

  private static final class RecordingVisitor implements PlaceholderValueVisitor<String> {
    @Override
    public String visitScalar(ScalarPlaceholderValue value) {
      return "scalar:" + value.value();
    }

    @Override
    public String visitList(ListPlaceholderValue value) {
      return "list:" + value.items().size();
    }

    @Override
    public String visitMissing(MissingPlaceholderValue value) {
      return "missing";
    }
  }

  @Test
  void placeholderWrapsDottedKey() {
    assertThat(Placeholder.of("invoice.number").key()).isEqualTo("invoice.number");
  }

  @Test
  void scalarDispatchesToVisitScalar() {
    PlaceholderValue value = ScalarPlaceholderValue.of("500.00");
    assertThat(value.accept(new RecordingVisitor())).isEqualTo("scalar:500.00");
  }

  @Test
  void listDispatchesToVisitList() {
    PlaceholderValue value = ListPlaceholderValue.of(List.of());
    assertThat(value.accept(new RecordingVisitor())).isEqualTo("list:0");
  }

  @Test
  void missingDispatchesToVisitMissingAndIsSingleton() {
    PlaceholderValue value = MissingPlaceholderValue.instance();
    assertThat(value.accept(new RecordingVisitor())).isEqualTo("missing");
    assertThat(MissingPlaceholderValue.instance()).isSameAs(value);
  }

  @Test
  void placeholderThrowsValidationExceptionOnNull() {
    assertThatThrownBy(() -> Placeholder.of(null)).isInstanceOf(ValidationException.class);
  }

  @Test
  void placeholderThrowsValidationExceptionOnBlank() {
    assertThatThrownBy(() -> Placeholder.of("")).isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> Placeholder.of("   ")).isInstanceOf(ValidationException.class);
  }

  @Test
  void listPlaceholderValueHandlesNullItems() {
    assertThatThrownBy(() -> ListPlaceholderValue.of(null))
        .isInstanceOf(NullPointerException.class);
  }
}
