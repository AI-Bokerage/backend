package io.genfin.document.api.placeholder;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.port.PlaceholderResolver;
import org.junit.jupiter.api.Test;

class PlaceholderContextTest {

  @Test
  void resolvesThroughWrappedResolverWhenNoLocalOverride() {
    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("Acme");
    PlaceholderContext context = PlaceholderContext.of(resolver);

    assertThat(context.resolveDisplay("company.name")).isEqualTo("Acme");
  }

  @Test
  void localOverrideShadowsWrappedResolver() {
    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("Acme");
    PlaceholderContext context =
        PlaceholderContext.of(resolver)
            .withLocal("company.name", ScalarPlaceholderValue.of("Override"));

    assertThat(context.resolveDisplay("company.name")).isEqualTo("Override");
  }

  @Test
  void missingValueDisplaysAsEmptyString() {
    PlaceholderResolver resolver = key -> MissingPlaceholderValue.instance();
    PlaceholderContext context = PlaceholderContext.of(resolver);

    assertThat(context.resolveDisplay("unknown")).isEmpty();
  }

  @Test
  void registeredFormatterAppliesToDisplay() {
    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("1000");
    PlaceholderContext context =
        PlaceholderContext.of(resolver).withFormatter("total", raw -> "$" + raw);

    assertThat(context.resolveDisplay("total")).isEqualTo("$1000");
  }

  @Test
  void listValueDisplaysAsEmptyString() {
    PlaceholderResolver resolver = key -> ListPlaceholderValue.of(java.util.List.of());
    PlaceholderContext context = PlaceholderContext.of(resolver);

    assertThat(context.resolveDisplay("items")).isEmpty();
  }
}
