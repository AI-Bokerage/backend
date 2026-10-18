package io.genfin.ledger.posting;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.ledger.internal.posting.DefaultPostingEngine;
import io.genfin.ledger.port.posting.PostingEngine;
import io.genfin.ledger.port.posting.PostingPolicy;
import io.genfin.ledger.port.posting.PostingValidator;

/** Factory for {@link PostingEngine} instances. */
public final class PostingEngines {

  private PostingEngines() {}

  public static PostingEngine of(PostingPolicy policy, PostingValidator validator) {
    return new DefaultPostingEngine(policy, validator);
  }

  /**
   * Resolves the {@link PostingEngine} registered in {@code registry} if present; otherwise builds
   * one from the registered {@link PostingPolicy} (required - Gen-Fin has no default posting
   * mapping to fall back to) and {@link PostingValidator} (falling back to {@link
   * PostingValidators#standard()}).
   */
  public static PostingEngine from(ExtensionRegistry registry) {
    return registry
        .find(PostingEngine.class)
        .orElseGet(
            () -> {
              PostingPolicy policy =
                  registry
                      .find(PostingPolicy.class)
                      .orElseThrow(
                          () ->
                              new IllegalStateException(
                                  "No PostingPolicy registered - register one via "
                                      + "ExtensionRegistry before resolving a PostingEngine."));
              PostingValidator validator =
                  registry.find(PostingValidator.class).orElseGet(PostingValidators::standard);
              return of(policy, validator);
            });
  }
}
