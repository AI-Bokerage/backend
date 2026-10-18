package io.genfin.dunning.retry;

import io.genfin.dunning.internal.retry.DefaultRetryPolicy;
import io.genfin.dunning.port.retry.RetryPolicy;

/** Factory for {@link RetryPolicy} instances. */
public final class RetryPolicies {

  private RetryPolicies() {}

  public static RetryPolicy of(RetryPolicyConfiguration configuration) {
    return new DefaultRetryPolicy(configuration);
  }

  /**
   * A policy built from a fully-defaulted example configuration - see {@link
   * RetryPolicyConfiguration.Builder}.
   */
  public static RetryPolicy standard() {
    return of(RetryPolicyConfiguration.builder().build());
  }
}
