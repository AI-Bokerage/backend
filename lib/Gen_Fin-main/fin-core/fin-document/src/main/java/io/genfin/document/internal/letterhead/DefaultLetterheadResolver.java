package io.genfin.document.internal.letterhead;

import io.genfin.document.api.exception.LetterheadNotFoundException;
import io.genfin.document.api.identity.LetterheadId;
import io.genfin.document.api.letterhead.Letterhead;
import io.genfin.document.port.LetterheadProvider;
import io.genfin.document.port.LetterheadResolver;

public final class DefaultLetterheadResolver implements LetterheadResolver {

  private final DefaultLetterheadRegistry registry;

  public DefaultLetterheadResolver(DefaultLetterheadRegistry registry) {
    this.registry = registry;
  }

  @Override
  public Letterhead resolve(LetterheadId id) {
    for (LetterheadProvider provider : registry.providers()) {
      if (provider.supports(id)) {
        return provider.resolve(id);
      }
    }
    throw new LetterheadNotFoundException(id);
  }
}
