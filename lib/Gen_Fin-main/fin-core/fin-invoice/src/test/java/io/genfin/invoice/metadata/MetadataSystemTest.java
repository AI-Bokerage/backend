package io.genfin.invoice.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetadataSystemTest {

  @Test
  void typedValueRejectsWrongKindAccess() {
    TypedValue value = TypedValue.ofString("hello");

    assertThat(value.asString()).isEqualTo("hello");
    assertThatThrownBy(value::asNumber).isInstanceOf(ValidationException.class);
  }

  @Test
  void metadataIsQueryableByFieldName() {
    Metadata metadata =
        Metadata.of(
            List.of(
                new CustomField("poNumber", TypedValue.ofString("PO-99")),
                new CustomField("weight", TypedValue.ofNumber(new BigDecimal("12.5")))));

    assertThat(metadata.find("poNumber")).contains(TypedValue.ofString("PO-99"));
    assertThat(metadata.find("missing")).isEmpty();
  }

  @Test
  void withAddsOrReplacesAField() {
    Metadata metadata = Metadata.empty().with(new CustomField("x", TypedValue.ofBoolean(true)));

    assertThat(metadata.find("x")).contains(TypedValue.ofBoolean(true));
  }

  @Test
  void attributesAreFlatStringPairs() {
    Attributes attributes = new Attributes(java.util.Map.of("channel", "web"));

    assertThat(attributes.find("channel")).contains("web");
    assertThat(attributes.find("missing")).isEmpty();
  }

  @Test
  void extensionPropertiesAreNamespaced() {
    ExtensionProperties properties =
        ExtensionProperties.empty()
            .with("pluginA", Metadata.of(List.of(new CustomField("k", TypedValue.ofString("v")))));

    assertThat(properties.find("pluginA")).isPresent();
    assertThat(properties.find("pluginB")).isEmpty();
  }
}
