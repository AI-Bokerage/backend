package io.genfin.document.port;

import io.genfin.document.api.template.DocumentTemplate;
import io.genfin.document.internal.template.DefaultTemplateEngine;
import io.genfin.document.internal.template.DefaultTemplateRegistry;
import io.genfin.document.internal.template.DefaultTemplateResolver;

/**
 * Factory for a standard, ready-to-use {@link TemplateEngine} plus a handle to register {@link
 * DocumentTemplate}s against it. This is the only consumer-reachable way to obtain a working {@link
 * TemplateEngine}: the concrete registry/resolver/engine implementations all live in internal
 * packages that the module never exports.
 */
public final class TemplateEngines {

  private TemplateEngines() {}

  public static Bundle standard() {
    DefaultTemplateRegistry registry = new DefaultTemplateRegistry();
    TemplateEngine engine = new DefaultTemplateEngine(new DefaultTemplateResolver(registry));
    return new Bundle() {
      @Override
      public void register(DocumentTemplate template) {
        registry.register(template);
      }

      @Override
      public TemplateEngine engine() {
        return engine;
      }
    };
  }

  /** Registration handle and working engine, wired together against the same backing registry. */
  public interface Bundle {
    void register(DocumentTemplate template);

    TemplateEngine engine();
  }
}
