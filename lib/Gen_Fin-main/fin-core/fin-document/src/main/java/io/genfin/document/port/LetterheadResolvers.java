package io.genfin.document.port;

import io.genfin.document.internal.letterhead.DefaultLetterheadRegistry;
import io.genfin.document.internal.letterhead.DefaultLetterheadResolver;

/**
 * Factory for a standard, ready-to-use {@link LetterheadResolver} plus a handle to register {@link
 * LetterheadProvider}s against it. This is the only consumer-reachable way to obtain a working
 * {@link LetterheadResolver}: the concrete registry/resolver implementations all live in internal
 * packages that the module never exports.
 */
public final class LetterheadResolvers {

  private LetterheadResolvers() {}

  public static Bundle standard() {
    DefaultLetterheadRegistry registry = new DefaultLetterheadRegistry();
    DefaultLetterheadResolver resolver = new DefaultLetterheadResolver(registry);
    return new Bundle() {
      @Override
      public void register(LetterheadProvider provider) {
        registry.register(provider);
      }

      @Override
      public LetterheadResolver resolver() {
        return resolver;
      }
    };
  }

  /** Registration handle and working resolver, wired together against the same backing registry. */
  public interface Bundle {
    void register(LetterheadProvider provider);

    LetterheadResolver resolver();
  }
}
