package io.genfin.document.port;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentElementVisitor;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.KeyValueElement;
import io.genfin.document.api.model.TableElement;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.api.template.DocumentTemplate;
import io.genfin.document.api.template.TemplateContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateEnginesTest {

  @Test
  void standardBundleRegistersAndRendersATemplateEndToEnd() {
    TemplateEngines.Bundle bundle = TemplateEngines.standard();
    bundle.register(DocumentTemplate.of(TemplateId.of("greeting"), "Hello {{name}}"));

    PlaceholderResolvers.Bundle resolvers = PlaceholderResolvers.standard();
    resolvers.register(
        new PlaceholderProvider() {
          @Override
          public boolean supports(String key) {
            return "name".equals(key);
          }

          @Override
          public io.genfin.document.api.placeholder.PlaceholderValue resolve(String key) {
            return ScalarPlaceholderValue.of("World");
          }
        });

    List<DocumentSection> sections =
        bundle.engine().render(TemplateId.of("greeting"), TemplateContext.of(resolvers.resolver()));

    String rendered = flatten(sections);
    assertThat(rendered).contains("Hello ", "World");
  }

  private static String flatten(List<DocumentSection> sections) {
    return sections.stream()
        .flatMap(s -> s.elements().stream())
        .map(e -> e.accept(new TextExtractingVisitor()))
        .reduce("", String::concat);
  }

  private static final class TextExtractingVisitor implements DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(KeyValueElement element) {
      return element.value();
    }

    @Override
    public String visitTable(TableElement element) {
      return "";
    }

    @Override
    public String visitTextBlock(TextBlockElement element) {
      return element.text();
    }
  }
}
