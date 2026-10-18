package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.placeholder.ListPlaceholderValue;
import io.genfin.document.api.placeholder.MissingPlaceholderValue;
import io.genfin.document.api.placeholder.PlaceholderContext;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.api.template.DocumentTemplate;
import io.genfin.document.api.template.TemplateContext;
import io.genfin.document.api.template.TemplateVariables;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.TemplateEngine;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultTemplateEngineTest {

  @Test
  void rendersLiteralAndPlaceholderIntoSection() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("greeting"), "Hello {{name}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("World");
    List<DocumentSection> sections =
        engine.render(TemplateId.of("greeting"), TemplateContext.of(resolver));

    assertThat(sections).isNotEmpty();
    String flattened =
        sections.stream()
            .flatMap(s -> s.elements().stream())
            .map(e -> e.accept(new TextExtractingVisitor()))
            .reduce("", String::concat);
    assertThat(flattened).contains("Hello ");
    assertThat(flattened).contains("World");
  }

  @Test
  void ifBlockIncludedOnlyWhenConditionTruthy() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(
        DocumentTemplate.of(TemplateId.of("conditional"), "{{#if show}}Visible{{/if}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver truthy = key -> ScalarPlaceholderValue.of("yes");
    PlaceholderResolver falsy = key -> ScalarPlaceholderValue.of("");

    String withTruthy =
        flatten(engine.render(TemplateId.of("conditional"), TemplateContext.of(truthy)));
    String withFalsy =
        flatten(engine.render(TemplateId.of("conditional"), TemplateContext.of(falsy)));

    assertThat(withTruthy).contains("Visible");
    assertThat(withFalsy).doesNotContain("Visible");
  }

  @Test
  void eachBlockRepeatsBodyPerItem() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(
        DocumentTemplate.of(TemplateId.of("list"), "{{#each items}}{{item}};{{/each}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver resolver =
        key ->
            "items".equals(key)
                ? ListPlaceholderValue.of(
                    List.of(
                        PlaceholderContext.of(k -> ScalarPlaceholderValue.of("a")),
                        PlaceholderContext.of(k -> ScalarPlaceholderValue.of("b"))))
                : ScalarPlaceholderValue.of("");

    String flattened = flatten(engine.render(TemplateId.of("list"), TemplateContext.of(resolver)));

    assertThat(flattened).contains("a;");
    assertThat(flattened).contains("b;");
  }

  @Test
  void eachBlockFallsBackToOuterContextForKeysNotInItemScope() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(
        DocumentTemplate.of(
            TemplateId.of("invoice"), "{{#each lines}}{{company}}: {{item}};{{/each}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver outer =
        key ->
            "company".equals(key)
                ? ScalarPlaceholderValue.of("Acme")
                : "lines".equals(key)
                    ? ListPlaceholderValue.of(
                        List.of(
                            PlaceholderContext.of(itemResolver("a")),
                            PlaceholderContext.of(itemResolver("b"))))
                    : MissingPlaceholderValue.instance();

    String flattened = flatten(engine.render(TemplateId.of("invoice"), TemplateContext.of(outer)));

    assertThat(flattened).contains("Acme: a;");
    assertThat(flattened).contains("Acme: b;");
  }

  @Test
  void formatterRegisteredOnTemplateContextAppliesWhenRenderingThroughEngine() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(DocumentTemplate.of(TemplateId.of("amount"), "Total: {{amount}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver resolver = key -> ScalarPlaceholderValue.of("1234.5");
    TemplateContext context =
        TemplateContext.of(resolver).withFormatter("amount", raw -> "$" + raw);

    String flattened = flatten(engine.render(TemplateId.of("amount"), context));

    assertThat(flattened).contains("Total: $1234.5");
  }

  @Test
  void formatterRegisteredOnTemplateContextAppliesInsideEachBodyForOuterScopedKey() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(
        DocumentTemplate.of(
            TemplateId.of("invoice-amount"), "{{#each lines}}{{amount}}: {{item}};{{/each}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver outer =
        key ->
            "amount".equals(key)
                ? ScalarPlaceholderValue.of("1234.5")
                : "lines".equals(key)
                    ? ListPlaceholderValue.of(
                        List.of(
                            PlaceholderContext.of(itemResolver("a")),
                            PlaceholderContext.of(itemResolver("b"))))
                    : MissingPlaceholderValue.instance();

    TemplateContext context = TemplateContext.of(outer).withFormatter("amount", raw -> "$" + raw);

    String flattened = flatten(engine.render(TemplateId.of("invoice-amount"), context));

    assertThat(flattened).contains("$1234.5: a;");
    assertThat(flattened).contains("$1234.5: b;");
  }

  @Test
  void itemValueWinsOverOuterLocalSharingTheSameKeyInsideEachBody() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(
        DocumentTemplate.of(TemplateId.of("clash"), "{{#each lines}}{{amount}};{{/each}}"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    PlaceholderResolver outerResolver =
        key ->
            "lines".equals(key)
                ? ListPlaceholderValue.of(
                    List.of(PlaceholderContext.of(itemResolver("amount", "ITEMVAL"))))
                : MissingPlaceholderValue.instance();
    TemplateContext context =
        TemplateContext.of(
            outerResolver,
            TemplateVariables.of(Map.of("amount", ScalarPlaceholderValue.of("OUTERLOCAL"))));

    String flattened = flatten(engine.render(TemplateId.of("clash"), context));

    assertThat(flattened).contains("ITEMVAL;");
    assertThat(flattened).doesNotContain("OUTERLOCAL");
  }

  @Test
  void namedSectionsProduceSeparatelyTitledDocumentSections() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    registry.register(
        DocumentTemplate.of(
            TemplateId.of("layout"),
            "OUTER{{#section \"totals\"}}Totals body{{/section}}"
                + "MIDDLE{{#section \"notes\"}}Notes body{{/section}}TAIL"));
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));

    List<DocumentSection> sections =
        engine.render(
            TemplateId.of("layout"), TemplateContext.of(key -> ScalarPlaceholderValue.of("")));

    assertThat(sections.stream().map(DocumentSection::title))
        .contains("totals", "notes")
        .contains("");

    String totalsText = flatten(sections.stream().filter(s -> "totals".equals(s.title())).toList());
    String notesText = flatten(sections.stream().filter(s -> "notes".equals(s.title())).toList());
    assertThat(totalsText).contains("Totals body");
    assertThat(notesText).contains("Notes body");
  }

  private static PlaceholderResolver itemResolver(String itemValue) {
    return itemResolver("item", itemValue);
  }

  private static PlaceholderResolver itemResolver(String key, String value) {
    return k ->
        k.equals(key) ? ScalarPlaceholderValue.of(value) : MissingPlaceholderValue.instance();
  }

  private static String flatten(List<DocumentSection> sections) {
    return sections.stream()
        .flatMap(s -> s.elements().stream())
        .map(e -> e.accept(new TextExtractingVisitor()))
        .reduce("", String::concat);
  }

  private static final class TextExtractingVisitor
      implements io.genfin.document.api.model.DocumentElementVisitor<String> {
    @Override
    public String visitKeyValue(io.genfin.document.api.model.KeyValueElement element) {
      return element.value();
    }

    @Override
    public String visitTable(io.genfin.document.api.model.TableElement element) {
      return "";
    }

    @Override
    public String visitTextBlock(io.genfin.document.api.model.TextBlockElement element) {
      return element.text();
    }
  }
}
