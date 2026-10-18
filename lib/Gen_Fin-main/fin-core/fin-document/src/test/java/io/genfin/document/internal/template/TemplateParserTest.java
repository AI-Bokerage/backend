package io.genfin.document.internal.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.template.EachFragment;
import io.genfin.document.api.template.IfFragment;
import io.genfin.document.api.template.IncludeFragment;
import io.genfin.document.api.template.LiteralFragment;
import io.genfin.document.api.template.PlaceholderFragment;
import io.genfin.document.api.template.TemplateFragment;
import org.junit.jupiter.api.Test;

class TemplateParserTest {

  @Test
  void parsesLiteralOnly() {
    ParsedTemplate parsed = TemplateParser.parse("hello world");
    assertThat(parsed.topLevelFragments()).hasSize(1);
    assertThat(((LiteralFragment) parsed.topLevelFragments().get(0)).text())
        .isEqualTo("hello world");
  }

  @Test
  void parsesLiteralAndPlaceholder() {
    ParsedTemplate parsed = TemplateParser.parse("Hello {{name}}!");
    assertThat(parsed.topLevelFragments()).hasSize(3);
    TemplateFragment middle = parsed.topLevelFragments().get(1);
    assertThat(((PlaceholderFragment) middle).placeholder().key()).isEqualTo("name");
  }

  @Test
  void parsesIfBlock() {
    ParsedTemplate parsed = TemplateParser.parse("{{#if hasNotes}}Notes: {{notes}}{{/if}}");
    assertThat(parsed.topLevelFragments()).hasSize(1);
    IfFragment fragment = (IfFragment) parsed.topLevelFragments().get(0);
    assertThat(fragment.condition().key()).isEqualTo("hasNotes");
    assertThat(fragment.body()).hasSize(2);
  }

  @Test
  void parsesEachBlock() {
    ParsedTemplate parsed = TemplateParser.parse("{{#each items}}{{name}}{{/each}}");
    EachFragment fragment = (EachFragment) parsed.topLevelFragments().get(0);
    assertThat(fragment.collection().key()).isEqualTo("items");
    assertThat(fragment.body()).hasSize(1);
  }

  @Test
  void parsesIncludeDirective() {
    ParsedTemplate parsed = TemplateParser.parse("{{include \"header\"}}");
    IncludeFragment fragment = (IncludeFragment) parsed.topLevelFragments().get(0);
    assertThat(fragment.includedTemplateId().value()).isEqualTo("header");
  }

  @Test
  void parsesNamedSectionSeparatelyFromTopLevelFragments() {
    ParsedTemplate parsed = TemplateParser.parse("{{#section \"body\"}}Hello{{/section}}");
    assertThat(parsed.sections()).hasSize(1);
    assertThat(parsed.sections().get(0).name()).isEqualTo("body");
    assertThat(parsed.topLevelFragments()).isEmpty();
  }

  @Test
  void duplicateSectionNameInSameTemplateThrowsValidationException() {
    assertThatThrownBy(
            () ->
                TemplateParser.parse(
                    "{{#section \"a\"}}X{{/section}}{{#section \"a\"}}Y{{/section}}"))
        .isInstanceOf(ValidationException.class)
        .hasMessageContaining("a");
  }

  @Test
  void unmatchedIfThrowsValidationException() {
    assertThatThrownBy(() -> TemplateParser.parse("{{#if x}}unterminated"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void unmatchedEachThrowsValidationException() {
    assertThatThrownBy(() -> TemplateParser.parse("{{#each x}}unterminated"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void malformedDelimiterThrowsValidationException() {
    assertThatThrownBy(() -> TemplateParser.parse("{{unterminated"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void unknownDirectiveThrowsValidationException() {
    assertThatThrownBy(() -> TemplateParser.parse("{{#unless x}}body{{/unless}}"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void nullSourceThrowsValidationException() {
    assertThatThrownBy(() -> TemplateParser.parse(null)).isInstanceOf(ValidationException.class);
  }

  @Test
  void parsesNestedIfInsideEach() {
    ParsedTemplate parsed = TemplateParser.parse("{{#each items}}{{#if x}}a{{/if}}{{/each}}");
    EachFragment each = (EachFragment) parsed.topLevelFragments().get(0);
    assertThat(each.collection().key()).isEqualTo("items");
    assertThat(each.body()).hasSize(1);
    IfFragment nestedIf = (IfFragment) each.body().get(0);
    assertThat(nestedIf.condition().key()).isEqualTo("x");
    assertThat(nestedIf.body()).hasSize(1);
    assertThat(((LiteralFragment) nestedIf.body().get(0)).text()).isEqualTo("a");
  }
}
