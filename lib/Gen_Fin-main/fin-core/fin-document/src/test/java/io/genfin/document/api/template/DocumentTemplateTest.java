package io.genfin.document.api.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.placeholder.PlaceholderContext;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.port.PlaceholderResolver;
import org.junit.jupiter.api.Test;

class DocumentTemplateTest {

  @Test
  void templateWithoutParentHasEmptyParentId() {
    DocumentTemplate template = DocumentTemplate.of(TemplateId.of("invoice"), "hello {{name}}");
    assertThat(template.parentId()).isEmpty();
  }

  @Test
  void templateWithParentExposesIt() {
    DocumentTemplate template =
        DocumentTemplate.withParent(TemplateId.of("child"), "body", TemplateId.of("parent"));
    assertThat(template.parentId()).contains(TemplateId.of("parent"));
  }

  @Test
  void templateVariablesLookup() {
    TemplateVariables variables =
        TemplateVariables.of(java.util.Map.of("greeting", ScalarPlaceholderValue.of("hi")));
    assertThat(variables.get("greeting")).isPresent();
    assertThat(variables.get("missing")).isEmpty();
  }

  @Test
  void templateContextSeedsPlaceholderContextWithInitialVariables() {
    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("fallback");
    TemplateVariables initial =
        TemplateVariables.of(java.util.Map.of("greeting", ScalarPlaceholderValue.of("hi")));
    TemplateContext context = TemplateContext.of(resolver, initial);

    PlaceholderContext placeholderContext = context.toPlaceholderContext();

    assertThat(placeholderContext.resolveDisplay("greeting")).isEqualTo("hi");
    assertThat(placeholderContext.resolveDisplay("other")).isEqualTo("fallback");
  }
}
