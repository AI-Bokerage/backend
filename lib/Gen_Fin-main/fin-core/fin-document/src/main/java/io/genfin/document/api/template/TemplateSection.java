package io.genfin.document.api.template;

import java.util.List;

public final class TemplateSection {

  private final String name;
  private final List<TemplateFragment> body;

  private TemplateSection(String name, List<TemplateFragment> body) {
    this.name = name;
    this.body = List.copyOf(body);
  }

  public static TemplateSection of(String name, List<TemplateFragment> body) {
    return new TemplateSection(name, body);
  }

  public String name() {
    return name;
  }

  public List<TemplateFragment> body() {
    return body;
  }
}
