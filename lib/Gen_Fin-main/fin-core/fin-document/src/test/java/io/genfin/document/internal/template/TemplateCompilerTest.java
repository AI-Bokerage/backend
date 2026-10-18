package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;
import io.genfin.document.api.template.DocumentTemplate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateCompilerTest {

  @Test
  void compilesFlatTemplateWithNoParent() {
    DocumentTemplate template = DocumentTemplate.of(TemplateId.of("simple"), "Hello {{name}}");
    CompiledTemplate compiled = TemplateCompiler.compile(template, id -> null);

    assertThat(compiled.id()).isEqualTo(TemplateId.of("simple"));
    assertThat(compiled.fragments()).hasSize(2);
  }

  @Test
  void sectionKeepsItsOriginalPositionAmongSurroundingLiterals() {
    DocumentTemplate template =
        DocumentTemplate.of(
            TemplateId.of("layout"), "BEFORE{{#section \"s\"}}SEC{{/section}}AFTER");

    CompiledTemplate compiled = TemplateCompiler.compile(template, id -> null);

    String rendered =
        compiled.fragments().stream()
            .map(f -> f.accept(new LiteralConcatenatingVisitor()))
            .reduce("", String::concat);

    assertThat(rendered).isEqualTo("BEFORESECAFTER");
  }

  @Test
  void childSectionOverridesMatchingParentSection() {
    DocumentTemplate parent =
        DocumentTemplate.of(
            TemplateId.of("parent"),
            "{{#section \"header\"}}ParentHeader{{/section}}{{#section \"body\"}}ParentBody{{/section}}");
    DocumentTemplate child =
        DocumentTemplate.withParent(
            TemplateId.of("child"),
            "{{#section \"body\"}}ChildBody{{/section}}",
            TemplateId.of("parent"));

    Map<TemplateId, DocumentTemplate> registry = Map.of(TemplateId.of("parent"), parent);
    CompiledTemplate compiled = TemplateCompiler.compile(child, registry::get);

    String rendered =
        compiled.fragments().stream()
            .map(f -> f.accept(new LiteralConcatenatingVisitor()))
            .reduce("", String::concat);

    assertThat(rendered).contains("ParentHeader");
    assertThat(rendered).contains("ChildBody");
    assertThat(rendered).doesNotContain("ParentBody");
  }

  @Test
  void includeIsInlinedAtCompileTime() {
    DocumentTemplate included = DocumentTemplate.of(TemplateId.of("footer"), "FooterText");
    DocumentTemplate main = DocumentTemplate.of(TemplateId.of("main"), "{{include \"footer\"}}");

    Map<TemplateId, DocumentTemplate> registry = Map.of(TemplateId.of("footer"), included);
    CompiledTemplate compiled = TemplateCompiler.compile(main, registry::get);

    String rendered =
        compiled.fragments().stream()
            .map(f -> f.accept(new LiteralConcatenatingVisitor()))
            .reduce("", String::concat);

    assertThat(rendered).contains("FooterText");
  }

  @Test
  void missingParentThrowsValidationException() {
    DocumentTemplate child =
        DocumentTemplate.withParent(
            TemplateId.of("child"), "body", TemplateId.of("missing-parent"));

    assertThatThrownBy(() -> TemplateCompiler.compile(child, id -> null))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void cyclicIncludeThrowsValidationException() {
    DocumentTemplate a = DocumentTemplate.of(TemplateId.of("a"), "{{include \"b\"}}");
    DocumentTemplate b = DocumentTemplate.of(TemplateId.of("b"), "{{include \"a\"}}");
    Map<TemplateId, DocumentTemplate> registry =
        Map.of(TemplateId.of("a"), a, TemplateId.of("b"), b);

    assertThatThrownBy(() -> TemplateCompiler.compile(a, registry::get))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void selfParentThrowsValidationException() {
    DocumentTemplate selfParenting =
        DocumentTemplate.withParent(TemplateId.of("self"), "body", TemplateId.of("self"));
    Map<TemplateId, DocumentTemplate> registry = Map.of(TemplateId.of("self"), selfParenting);

    assertThatThrownBy(() -> TemplateCompiler.compile(selfParenting, registry::get))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void cyclicParentChainThrowsValidationException() {
    DocumentTemplate a =
        DocumentTemplate.withParent(TemplateId.of("a"), "bodyA", TemplateId.of("b"));
    DocumentTemplate b =
        DocumentTemplate.withParent(TemplateId.of("b"), "bodyB", TemplateId.of("a"));
    Map<TemplateId, DocumentTemplate> registry =
        Map.of(TemplateId.of("a"), a, TemplateId.of("b"), b);

    assertThatThrownBy(() -> TemplateCompiler.compile(a, registry::get))
        .isInstanceOf(ValidationException.class);
  }

  private static final class LiteralConcatenatingVisitor
      implements io.genfin.document.api.template.TemplateFragmentVisitor<String> {
    @Override
    public String visitLiteral(io.genfin.document.api.template.LiteralFragment fragment) {
      return fragment.text();
    }

    @Override
    public String visitPlaceholder(io.genfin.document.api.template.PlaceholderFragment fragment) {
      return "";
    }

    @Override
    public String visitIf(io.genfin.document.api.template.IfFragment fragment) {
      return fragment.body().stream().map(f -> f.accept(this)).reduce("", String::concat);
    }

    @Override
    public String visitEach(io.genfin.document.api.template.EachFragment fragment) {
      return fragment.body().stream().map(f -> f.accept(this)).reduce("", String::concat);
    }

    @Override
    public String visitInclude(io.genfin.document.api.template.IncludeFragment fragment) {
      return "";
    }
  }
}
