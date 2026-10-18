package io.genfin.document.internal.placeholder;

import io.genfin.document.port.PlaceholderProvider;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultPlaceholderRegistry {

  private final List<PlaceholderProvider> providers = new CopyOnWriteArrayList<>();

  public void register(PlaceholderProvider provider) {
    providers.add(provider);
  }

  public List<PlaceholderProvider> providers() {
    return List.copyOf(providers);
  }
}
