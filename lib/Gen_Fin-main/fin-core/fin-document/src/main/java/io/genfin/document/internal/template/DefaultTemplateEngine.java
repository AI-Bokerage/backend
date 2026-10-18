package io.genfin.document.internal.template;

import io.genfin.document.api.identity.TemplateId;
import io.genfin.document.api.model.DocumentSection;
import io.genfin.document.api.model.TextBlockElement;
import io.genfin.document.api.placeholder.ListPlaceholderValue;
import io.genfin.document.api.placeholder.MissingPlaceholderValue;
import io.genfin.document.api.placeholder.PlaceholderContext;
import io.genfin.document.api.placeholder.PlaceholderValue;
import io.genfin.document.api.placeholder.PlaceholderValueVisitor;
import io.genfin.document.api.placeholder.ScalarPlaceholderValue;
import io.genfin.document.api.template.CompiledTemplate;
import io.genfin.document.api.template.EachFragment;
import io.genfin.document.api.template.IfFragment;
import io.genfin.document.api.template.IncludeFragment;
import io.genfin.document.api.template.LiteralFragment;
import io.genfin.document.api.template.PlaceholderFragment;
import io.genfin.document.api.template.TemplateContext;
import io.genfin.document.api.template.TemplateFragment;
import io.genfin.document.api.template.TemplateFragmentVisitor;
import io.genfin.document.port.PlaceholderResolver;
import io.genfin.document.port.TemplateEngine;
import io.genfin.document.port.TemplateResolver;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a {@link CompiledTemplate} and renders its fragment tree against a {@link
 * PlaceholderContext}, producing Stage 1's {@link DocumentSection}/{@link
 * io.genfin.document.api.model.DocumentElement} output.
 */
public final class DefaultTemplateEngine implements TemplateEngine {

  private static final PlaceholderValueVisitor<Boolean> TRUTHY =
      new PlaceholderValueVisitor<Boolean>() {
        @Override
        public Boolean visitScalar(ScalarPlaceholderValue value) {
          return !value.value().isBlank();
        }

        @Override
        public Boolean visitList(ListPlaceholderValue value) {
          return !value.items().isEmpty();
        }

        @Override
        public Boolean visitMissing(MissingPlaceholderValue value) {
          return false;
        }
      };

  private static final PlaceholderValueVisitor<Boolean> IS_MISSING =
      new PlaceholderValueVisitor<Boolean>() {
        @Override
        public Boolean visitScalar(ScalarPlaceholderValue value) {
          return false;
        }

        @Override
        public Boolean visitList(ListPlaceholderValue value) {
          return false;
        }

        @Override
        public Boolean visitMissing(MissingPlaceholderValue value) {
          return true;
        }
      };

  private final TemplateResolver resolver;

  public DefaultTemplateEngine(TemplateResolver resolver) {
    this.resolver = resolver;
  }

  @Override
  public List<DocumentSection> render(TemplateId templateId, TemplateContext context) {
    CompiledTemplate compiled = resolver.resolve(templateId);
    PlaceholderContext placeholderContext = context.toPlaceholderContext();
    List<DocumentSection> sections = new ArrayList<>();
    for (CompiledTemplate.Region region : compiled.regions()) {
      String text = renderFragments(region.fragments(), placeholderContext);
      sections.add(
          DocumentSection.of(region.name().orElse(""), List.of(TextBlockElement.of(text))));
    }
    return sections;
  }

  private static String renderFragments(
      List<TemplateFragment> fragments, PlaceholderContext context) {
    StringBuilder builder = new StringBuilder();
    FragmentRenderer renderer = new FragmentRenderer(context);
    for (TemplateFragment fragment : fragments) {
      builder.append(fragment.accept(renderer));
    }
    return builder.toString();
  }

  private static final class FragmentRenderer implements TemplateFragmentVisitor<String> {

    private final PlaceholderContext context;

    private FragmentRenderer(PlaceholderContext context) {
      this.context = context;
    }

    @Override
    public String visitLiteral(LiteralFragment fragment) {
      return fragment.text();
    }

    @Override
    public String visitPlaceholder(PlaceholderFragment fragment) {
      return context.resolveDisplay(fragment.placeholder().key());
    }

    @Override
    public String visitIf(IfFragment fragment) {
      PlaceholderValue value = context.resolveValue(fragment.condition().key());
      boolean truthy = value.accept(TRUTHY);
      return truthy ? renderFragments(fragment.body(), context) : "";
    }

    @Override
    public String visitEach(EachFragment fragment) {
      PlaceholderValue value = context.resolveValue(fragment.collection().key());
      return value.accept(new EachBodyRenderer(fragment.body(), context));
    }

    @Override
    public String visitInclude(IncludeFragment fragment) {
      // Unreachable: TemplateCompiler inlines every include at compile time.
      return "";
    }
  }

  private static final class EachBodyRenderer implements PlaceholderValueVisitor<String> {

    private final List<TemplateFragment> body;
    private final PlaceholderContext outer;

    private EachBodyRenderer(List<TemplateFragment> body, PlaceholderContext outer) {
      this.body = body;
      this.outer = outer;
    }

    @Override
    public String visitScalar(ScalarPlaceholderValue value) {
      return "";
    }

    @Override
    public String visitList(ListPlaceholderValue value) {
      StringBuilder builder = new StringBuilder();
      for (PlaceholderContext itemContext : value.items()) {
        PlaceholderContext combined = outer.withResolver(fallbackResolver(itemContext, outer));
        builder.append(renderFragments(body, combined));
      }
      return builder.toString();
    }

    @Override
    public String visitMissing(MissingPlaceholderValue value) {
      return "";
    }

    /**
     * Resolves a key against the item's own context first; falls back to the outer (enclosing
     * {{#each}}) context when the item's context has no value for that key, so outer-scope
     * placeholders stay reachable from inside the loop body.
     */
    private static PlaceholderResolver fallbackResolver(
        PlaceholderContext item, PlaceholderContext outer) {
      return key -> {
        PlaceholderValue value = item.resolveValue(key);
        boolean missing = value.accept(IS_MISSING);
        return missing ? outer.resolveValue(key) : value;
      };
    }
  }
}
