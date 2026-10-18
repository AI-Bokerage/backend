package io.genfin.document.api.template;

import io.genfin.document.api.identity.TemplateId;
import java.util.Optional;

public final class DocumentTemplate {

  private final TemplateId id;
  private final String rawSource;
  private final TemplateId parentId;

  private DocumentTemplate(TemplateId id, String rawSource, TemplateId parentId) {
    this.id = id;
    this.rawSource = rawSource == null ? "" : rawSource;
    this.parentId = parentId;
  }

  public static DocumentTemplate of(TemplateId id, String rawSource) {
    return new DocumentTemplate(id, rawSource, null);
  }

  public static DocumentTemplate withParent(TemplateId id, String rawSource, TemplateId parentId) {
    return new DocumentTemplate(id, rawSource, parentId);
  }

  public TemplateId id() {
    return id;
  }

  public String rawSource() {
    return rawSource;
  }

  public Optional<TemplateId> parentId() {
    return Optional.ofNullable(parentId);
  }
}
