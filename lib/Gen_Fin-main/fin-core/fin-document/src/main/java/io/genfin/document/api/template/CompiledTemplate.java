package io.genfin.document.api.template;

import io.genfin.document.api.identity.TemplateId;
import java.util.List;
import java.util.Optional;

/**
 * Fully resolved template output: parent chain merged, {@code {{include}}} directives inlined.
 * Produced by {@code io.genfin.document.internal.template.TemplateCompiler}.
 */
public final class CompiledTemplate {

  private final TemplateId id;
  private final List<TemplateFragment> fragments;
  private final List<Region> regions;

  private CompiledTemplate(TemplateId id, List<TemplateFragment> fragments, List<Region> regions) {
    this.id = id;
    this.fragments = List.copyOf(fragments);
    this.regions = List.copyOf(regions);
  }

  public static CompiledTemplate of(TemplateId id, List<TemplateFragment> fragments) {
    return new CompiledTemplate(id, fragments, List.of(Region.of(null, fragments)));
  }

  public static CompiledTemplate of(
      TemplateId id, List<TemplateFragment> fragments, List<Region> regions) {
    return new CompiledTemplate(id, fragments, regions);
  }

  public TemplateId id() {
    return id;
  }

  public List<TemplateFragment> fragments() {
    return fragments;
  }

  /**
   * The compiled fragments, grouped into document-order regions: one per named {@code
   * {{#section}}}, plus one per run of content outside any section. Lets a renderer preserve
   * section names as output boundaries (e.g. {@link io.genfin.document.api.model.DocumentSection}
   * titles) instead of flattening everything into a single blob.
   */
  public List<Region> regions() {
    return regions;
  }

  /** A named or unnamed run of fragments within a {@link CompiledTemplate}, in document order. */
  public static final class Region {

    private final String name;
    private final List<TemplateFragment> fragments;

    private Region(String name, List<TemplateFragment> fragments) {
      this.name = name;
      this.fragments = List.copyOf(fragments);
    }

    public static Region of(String name, List<TemplateFragment> fragments) {
      return new Region(name, fragments);
    }

    public Optional<String> name() {
      return Optional.ofNullable(name);
    }

    public List<TemplateFragment> fragments() {
      return fragments;
    }
  }
}
