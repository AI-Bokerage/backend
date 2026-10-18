package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentSectionTest {

  @Test
  void blankTitleIsAllowedForUntitledSections() {
    DocumentSection section = DocumentSection.of("", List.of());
    assertThat(section.title()).isEmpty();
  }

  @Test
  void ofRejectsNullTitle() {
    assertThatThrownBy(() -> DocumentSection.of(null, List.of()))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void ofRejectsNullElements() {
    assertThatThrownBy(() -> DocumentSection.of("Summary", null))
        .isInstanceOf(ValidationException.class);
  }
}
