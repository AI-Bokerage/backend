package io.genfin.dunning.policy;

import io.genfin.dunning.port.policy.DunningPolicy;

/** Entry point for building {@link DunningPolicy} instances. */
public final class DunningPolicies {

  private DunningPolicies() {}

  public static PolicyBuilder builder() {
    return PolicyBuilder.create();
  }

  /** A policy built from fully-defaulted example sub-policies - see {@link PolicyBuilder}. */
  public static DunningPolicy standard() {
    return builder().build();
  }
}
