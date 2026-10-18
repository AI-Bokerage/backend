package io.genfin.invoice.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceModelTest {

  private static final ReferenceType PROJECT = StandardReferenceType.of("PROJECT");
  private static final ReferenceType SUBSCRIPTION = StandardReferenceType.of("SUBSCRIPTION");

  @Test
  void referenceCarriesOpaqueTypeAndValue() {
    Reference reference = Reference.of(PROJECT, "PRJ-123", "Website Redesign");

    assertThat(reference.type().code()).isEqualTo("PROJECT");
    assertThat(reference.value().value()).isEqualTo("PRJ-123");
    assertThat(reference.label()).contains("Website Redesign");
  }

  @Test
  void blankValueIsRejected() {
    assertThatThrownBy(() -> Reference.of(PROJECT, " ")).isInstanceOf(ValidationException.class);
  }

  @Test
  void collectionFiltersByType() {
    ReferenceCollection collection =
        ReferenceCollection.of(
            List.of(
                Reference.of(PROJECT, "PRJ-1"),
                Reference.of(SUBSCRIPTION, "SUB-1"),
                Reference.of(PROJECT, "PRJ-2")));

    assertThat(collection.byType(PROJECT)).hasSize(2);
    assertThat(collection.byType(SUBSCRIPTION)).hasSize(1);
    assertThat(collection.groupedByTypeCode()).containsKeys("PROJECT", "SUBSCRIPTION");
  }

  @Test
  void addReturnsNewImmutableCollection() {
    ReferenceCollection original = ReferenceCollection.empty();
    ReferenceCollection updated = original.add(Reference.of(PROJECT, "PRJ-1"));

    assertThat(original.isEmpty()).isTrue();
    assertThat(updated.all()).hasSize(1);
  }
}
