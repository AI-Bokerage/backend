package io.genfin.document.internal.letterhead;

import io.genfin.document.port.LetterheadProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultLetterheadRegistry {

  private final List<LetterheadProvider> providers = new CopyOnWriteArrayList<>();

  public void register(LetterheadProvider provider) {
    providers.add(provider);
  }

  public List<LetterheadProvider> providers() {
    return List.copyOf(providers);
  }
}
