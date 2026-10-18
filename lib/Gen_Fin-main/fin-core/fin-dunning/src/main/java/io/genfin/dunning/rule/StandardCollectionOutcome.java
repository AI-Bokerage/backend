package io.genfin.dunning.rule;

import io.genfin.api.validation.Validate;

/**
 * A small convenience set of illustrative collection outcomes. Not exhaustive and purely
 * illustrative - fin-dunning is workflow-agnostic, so applications are free to {@link #of(String)}
 * any outcome code their own collection workflow actually acts on.
 */
public record StandardCollectionOutcome(String code) implements CollectionOutcome {

  public StandardCollectionOutcome {
    Validate.notBlank(code, "code must not be blank.");
  }

  public static StandardCollectionOutcome of(String code) {
    return new StandardCollectionOutcome(code);
  }

  /** Skip only today's scheduled action; the case remains otherwise on plan. */
  public static final StandardCollectionOutcome SKIP = of("SKIP");

  /** Hold the case entirely pending manual attention or an escalation decision. */
  public static final StandardCollectionOutcome HOLD = of("HOLD");

  /** The application has explicitly suspended collection for this case. */
  public static final StandardCollectionOutcome PAUSE = of("PAUSE");

  /** Flag the case for priority handling ahead of its normal schedule. */
  public static final StandardCollectionOutcome PRIORITIZE = of("PRIORITIZE");
}
