package io.genfin.document.internal.template;

import io.genfin.document.api.template.TemplateFragment;
import io.genfin.document.api.template.TemplateSection;
import java.util.List;

final class ParsedTemplate {

  private final List<TemplateFragment> topLevelFragments;
  private final List<TemplateSection> sections;
  private final List<Integer> sectionPositions;

  /**
   * @param sectionPositions parallel to {@code sections}: for a section declared directly among the
   *     top-level fragments, the index into {@code topLevelFragments} where it was encountered (so
   *     a compiler can splice it back into its original document position); -1 for a section
   *     declared inside a nested {@code {{#if}}}/{@code {{#each}}} block, where no top-level
   *     position applies.
   */
  ParsedTemplate(
      List<TemplateFragment> topLevelFragments,
      List<TemplateSection> sections,
      List<Integer> sectionPositions) {
    this.topLevelFragments = List.copyOf(topLevelFragments);
    this.sections = List.copyOf(sections);
    this.sectionPositions = List.copyOf(sectionPositions);
  }

  List<TemplateFragment> topLevelFragments() {
    return topLevelFragments;
  }

  List<TemplateSection> sections() {
    return sections;
  }

  List<Integer> sectionPositions() {
    return sectionPositions;
  }
}
