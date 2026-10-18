package io.genfin.document.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DocumentAttributesTest {

  @Test
  void emptyHasNoValues() {
    assertThat(DocumentAttributes.empty().get("anything")).isEmpty();
  }

  @Test
  void ofExposesGivenValues() {
    DocumentAttributes attributes = DocumentAttributes.of(Map.of("region", "IN"));
    assertThat(attributes.get("region")).contains("IN");
  }

  @Test
  void getReturnsEmptyForMissingKey() {
    DocumentAttributes attributes = DocumentAttributes.of(Map.of("region", "IN"));
    assertThat(attributes.get("language")).isEqualTo(Optional.empty());
  }

  @Test
  void asMapExposesAllEntries() {
    DocumentAttributes attributes = DocumentAttributes.of(Map.of("region", "IN", "unit", "kg"));
    assertThat(attributes.asMap()).containsEntry("region", "IN").containsEntry("unit", "kg");
  }
}
