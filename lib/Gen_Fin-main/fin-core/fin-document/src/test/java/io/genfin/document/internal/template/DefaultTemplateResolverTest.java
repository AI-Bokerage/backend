package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.document.api.exception.TemplateNotFoundException;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;
import io.genfin.document.api.template.DocumentTemplate;
import org.junit.jupiter.api.Test;

class DefaultTemplateResolverTest {

  @Test
  void resolvesRegisteredTemplate() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("simple"), "Hello {{name}}"));
    DefaultTemplateResolver resolver = new DefaultTemplateResolver(registry);

    CompiledTemplate compiled = resolver.resolve(TemplateId.of("simple"));

    assertThat(compiled.id()).isEqualTo(TemplateId.of("simple"));
    assertThat(compiled.fragments()).hasSize(2);
  }

  @Test
  void secondResolveReturnsCachedInstance() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("simple"), "Hello {{name}}"));
    DefaultTemplateResolver resolver = new DefaultTemplateResolver(registry);

    CompiledTemplate first = resolver.resolve(TemplateId.of("simple"));
    CompiledTemplate second = resolver.resolve(TemplateId.of("simple"));

    assertThat(first).isSameAs(second);
  }

  @Test
  void throwsForUnregisteredTemplate() {
    DefaultTemplateResolver resolver = new DefaultTemplateResolver(new DefaultTemplateRegistry());

    assertThatThrownBy(() -> resolver.resolve(TemplateId.of("missing")))
        .isInstanceOf(TemplateNotFoundException.class);
  }
}
