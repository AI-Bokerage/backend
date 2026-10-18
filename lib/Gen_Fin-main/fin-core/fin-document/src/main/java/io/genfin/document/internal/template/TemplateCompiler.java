package io.genfin.document.internal.template;

import io.genfin.api.exception.ValidationException;
import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.template.CompiledTemplate;
import io.genfin.document.api.template.DocumentTemplate;
import io.genfin.document.api.template.EachFragment;
import io.genfin.document.api.template.IfFragment;
import io.genfin.document.api.template.IncludeFragment;
import io.genfin.document.api.template.LiteralFragment;
import io.genfin.document.api.template.PlaceholderFragment;
import io.genfin.document.api.template.TemplateFragment;
import io.genfin.document.api.template.TemplateFragmentVisitor;
import io.genfin.document.api.template.TemplateSection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Resolves a {@link DocumentTemplate}'s parent chain (merging child section overrides into the
 * parent's output) and inlines {@code {{include}}} directives, producing a flat {@link
 * CompiledTemplate}. Guards against both cyclic parent chains and cyclic include chains.
 */
public final class TemplateCompiler {

  private TemplateCompiler() {}

  public static CompiledTemplate compile(
      DocumentTemplate template, Function<TemplateId, DocumentTemplate> templateLookup) {
    Compilation compilation = new Compilation(templateLookup);
    List<CompiledTemplate.Region> regions =
        compilation.withCycleGuard(template.id(), () -> compilation.compileRegions(template));
    List<TemplateFragment> fragments = new ArrayList<>();
    for (CompiledTemplate.Region region : regions) {
      fragments.addAll(region.fragments());
    }
    return CompiledTemplate.of(template.id(), fragments, regions);
  }

  /** Holds the lookup function and the "currently being resolved" id stack for cycle guards. */
  private static final class Compilation {
    private final Function<TemplateId, DocumentTemplate> lookup;
    private final Set<TemplateId> visiting = new HashSet<>();

    Compilation(Function<TemplateId, DocumentTemplate> lookup) {
      this.lookup = lookup;
    }

    private <T> T withCycleGuard(TemplateId id, Supplier<T> action) {
      if (!visiting.add(id)) {
        throw new ValidationException(
            "Cyclic template reference detected while resolving '" + id.value() + "'");
      }
      try {
        return action.get();
      } finally {
        visiting.remove(id);
      }
    }

    /**
     * Parent-chain merge and include-inlining for {@code template}; {@code template}'s own id is
     * assumed already guarded by the caller.
     */
    List<TemplateFragment> compileTemplate(DocumentTemplate template) {
      SectionBundle bundle = buildBundle(template);
      List<TemplateFragment> flat = new ArrayList<>();
      for (Slot slot : bundle.skeleton()) {
        flat.addAll(slot.resolve(bundle.sections()));
      }
      return inlineIncludes(flat);
    }

    /**
     * Like {@link #compileTemplate}, but groups the flattened output into document-order regions:
     * one per named {@code {{#section}}} slot, plus one per run of slots outside any section. Lets
     * a renderer preserve section names as output boundaries instead of one flattened blob.
     */
    List<CompiledTemplate.Region> compileRegions(DocumentTemplate template) {
      SectionBundle bundle = buildBundle(template);
      List<CompiledTemplate.Region> regions = new ArrayList<>();
      List<TemplateFragment> buffer = new ArrayList<>();
      for (Slot slot : bundle.skeleton()) {
        String sectionName = slot.sectionName();
        if (sectionName == null) {
          buffer.addAll(slot.resolve(bundle.sections()));
          continue;
        }
        if (!buffer.isEmpty()) {
          regions.add(CompiledTemplate.Region.of(null, inlineIncludes(buffer)));
          buffer = new ArrayList<>();
        }
        regions.add(
            CompiledTemplate.Region.of(
                sectionName, inlineIncludes(slot.resolve(bundle.sections()))));
      }
      if (!buffer.isEmpty()) {
        regions.add(CompiledTemplate.Region.of(null, inlineIncludes(buffer)));
      }
      return regions;
    }

    /**
     * Merges the template's own sections/top-level content with its resolved parent's, preserving
     * the original document order: a section's resolved content stays exactly where the {{
     * #section}} tag was written relative to surrounding literal/placeholder content, rather than
     * being hoisted to the front.
     */
    private SectionBundle buildBundle(DocumentTemplate template) {
      ParsedTemplate parsed = TemplateParser.parse(template.rawSource());

      if (template.parentId().isEmpty()) {
        List<TemplateFragment> topLevelFragments = parsed.topLevelFragments();
        List<TemplateSection> declaredSections = parsed.sections();
        List<Integer> positions = parsed.sectionPositions();

        LinkedHashMap<String, List<TemplateFragment>> sections = new LinkedHashMap<>();
        List<Slot> skeleton = new ArrayList<>();
        List<Slot> nestedSectionSlots = new ArrayList<>();
        int fragmentIndex = 0;
        for (int i = 0; i < declaredSections.size(); i++) {
          TemplateSection section = declaredSections.get(i);
          sections.put(section.name(), section.body());
          int position = positions.get(i);
          if (position < 0) {
            // Declared inside a nested if/each block: no top-level position to splice at, so
            // keep prior behavior of appending it after the rest of the top-level content.
            nestedSectionSlots.add(new SectionSlot(section.name()));
            continue;
          }
          while (fragmentIndex < position) {
            skeleton.add(new LiteralSlot(topLevelFragments.get(fragmentIndex)));
            fragmentIndex++;
          }
          skeleton.add(new SectionSlot(section.name()));
        }
        while (fragmentIndex < topLevelFragments.size()) {
          skeleton.add(new LiteralSlot(topLevelFragments.get(fragmentIndex)));
          fragmentIndex++;
        }
        skeleton.addAll(nestedSectionSlots);

        return new SectionBundle(sections, skeleton);
      }

      TemplateId parentId = template.parentId().orElseThrow();
      DocumentTemplate parent = lookup.apply(parentId);
      if (parent == null) {
        throw new ValidationException(
            "Template '"
                + template.id().value()
                + "' references unknown parent template '"
                + parentId.value()
                + "'");
      }

      SectionBundle parentBundle = withCycleGuard(parent.id(), () -> buildBundle(parent));

      LinkedHashMap<String, List<TemplateFragment>> sections =
          new LinkedHashMap<>(parentBundle.sections());
      List<Slot> skeleton = new ArrayList<>(parentBundle.skeleton());
      for (TemplateSection section : parsed.sections()) {
        boolean overridesExistingSlot = parentBundle.sections().containsKey(section.name());
        sections.put(section.name(), section.body());
        if (!overridesExistingSlot) {
          // A section the parent chain never declared: append it, matching the position a
          // brand-new (non-overriding) section would take at the leaf level.
          skeleton.add(new SectionSlot(section.name()));
        }
      }
      // The child's own top-level (non-section) content has no slot in the parent's skeleton to
      // occupy, so it's appended after everything the parent chain produced.
      for (TemplateFragment fragment : parsed.topLevelFragments()) {
        skeleton.add(new LiteralSlot(fragment));
      }

      return new SectionBundle(sections, skeleton);
    }

    /**
     * Splices every {@code {{include}}} directive (including ones nested inside if/each bodies)
     * with the fully-compiled fragments of its target template.
     */
    private List<TemplateFragment> inlineIncludes(List<TemplateFragment> fragments) {
      InlineIncludesVisitor visitor = new InlineIncludesVisitor();
      List<TemplateFragment> result = new ArrayList<>();
      for (TemplateFragment fragment : fragments) {
        result.addAll(fragment.accept(visitor));
      }
      return result;
    }

    private final class InlineIncludesVisitor
        implements TemplateFragmentVisitor<List<TemplateFragment>> {

      @Override
      public List<TemplateFragment> visitLiteral(LiteralFragment fragment) {
        return List.of(fragment);
      }

      @Override
      public List<TemplateFragment> visitPlaceholder(PlaceholderFragment fragment) {
        return List.of(fragment);
      }

      @Override
      public List<TemplateFragment> visitIf(IfFragment fragment) {
        return List.of(IfFragment.of(fragment.condition(), inlineIncludes(fragment.body())));
      }

      @Override
      public List<TemplateFragment> visitEach(EachFragment fragment) {
        return List.of(EachFragment.of(fragment.collection(), inlineIncludes(fragment.body())));
      }

      @Override
      public List<TemplateFragment> visitInclude(IncludeFragment fragment) {
        TemplateId includedId = fragment.includedTemplateId();
        DocumentTemplate included = lookup.apply(includedId);
        if (included == null) {
          throw new ValidationException(
              "Included template '" + includedId.value() + "' could not be resolved");
        }
        return withCycleGuard(includedId, () -> compileTemplate(included));
      }
    }
  }

  /**
   * Effective named sections (by name, overridden by descendants) plus the document-order skeleton
   * of slots that resolves against them to produce the final flat fragment list.
   */
  private record SectionBundle(
      LinkedHashMap<String, List<TemplateFragment>> sections, List<Slot> skeleton) {}

  /**
   * A position in the compiled output: either fixed content or a reference to a named section whose
   * (possibly overridden) body is resolved at flatten time.
   */
  private interface Slot {
    List<TemplateFragment> resolve(Map<String, List<TemplateFragment>> sections);

    /** The section name this slot resolves, or {@code null} if it holds fixed literal content. */
    default String sectionName() {
      return null;
    }
  }

  private record LiteralSlot(TemplateFragment fragment) implements Slot {
    @Override
    public List<TemplateFragment> resolve(Map<String, List<TemplateFragment>> sections) {
      return List.of(fragment);
    }
  }

  private record SectionSlot(String name) implements Slot {
    @Override
    public List<TemplateFragment> resolve(Map<String, List<TemplateFragment>> sections) {
      return sections.getOrDefault(name, List.of());
    }

    @Override
    public String sectionName() {
      return name;
    }
  }
}
