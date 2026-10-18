package io.genfin.document.api.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.placeholder.Placeholder;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateFragmentTest {

  private static final class RecordingVisitor implements TemplateFragmentVisitor<String> {
    @Override
    public String visitLiteral(LiteralFragment fragment) {
      return "literal:" + fragment.text();
    }

    @Override
    public String visitPlaceholder(PlaceholderFragment fragment) {
      return "placeholder:" + fragment.placeholder().key();
    }

    @Override
    public String visitIf(IfFragment fragment) {
      return "if:" + fragment.condition().key() + ":" + fragment.body().size();
    }

    @Override
    public String visitEach(EachFragment fragment) {
      return "each:" + fragment.collection().key() + ":" + fragment.body().size();
    }

    @Override
    public String visitInclude(IncludeFragment fragment) {
      return "include:" + fragment.includedTemplateId().value();
    }
  }

  @Test
  void literalDispatches() {
    assertThat(LiteralFragment.of("hello").accept(new RecordingVisitor()))
        .isEqualTo("literal:hello");
  }

  @Test
  void placeholderDispatches() {
    TemplateFragment fragment = PlaceholderFragment.of(Placeholder.of("invoice.number"));
    assertThat(fragment.accept(new RecordingVisitor())).isEqualTo("placeholder:invoice.number");
  }

  @Test
  void ifDispatches() {
    TemplateFragment fragment =
        IfFragment.of(Placeholder.of("hasNotes"), List.of(LiteralFragment.of("x")));
    assertThat(fragment.accept(new RecordingVisitor())).isEqualTo("if:hasNotes:1");
  }

  @Test
  void eachDispatches() {
    TemplateFragment fragment =
        EachFragment.of(Placeholder.of("items"), List.of(LiteralFragment.of("x")));
    assertThat(fragment.accept(new RecordingVisitor())).isEqualTo("each:items:1");
  }

  @Test
  void includeDispatches() {
    TemplateFragment fragment = IncludeFragment.of(TemplateId.of("header"));
    assertThat(fragment.accept(new RecordingVisitor())).isEqualTo("include:header");
  }

  @Test
  void sectionHoldsNameAndBody() {
    TemplateSection section = TemplateSection.of("body", List.of(LiteralFragment.of("x")));
    assertThat(section.name()).isEqualTo("body");
    assertThat(section.body()).hasSize(1);
  }
}
