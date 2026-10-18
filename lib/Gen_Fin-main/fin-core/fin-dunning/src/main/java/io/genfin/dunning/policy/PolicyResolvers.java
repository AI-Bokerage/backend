package io.genfin.dunning.policy;

import io.genfin.api.port.spi.Resolver;
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.internal.policy.DefaultPolicyResolver;
import io.genfin.dunning.obligation.ObligationType;
import io.genfin.dunning.port.policy.PolicyRegistry;
import io.genfin.dunning.port.policy.PolicyResolver;

/**
 * Factory for {@link PolicyResolver} instances. No zero-argument {@code standard()} - unlike a
 * default backoff/calendar, which {@link DunningPolicyId} applies to which {@link ObligationType}
 * is entirely an application decision that fin-dunning cannot default.
 */
public final class PolicyResolvers {

  private PolicyResolvers() {}

  public static PolicyResolver of(
      PolicyRegistry registry, Resolver<ObligationType, DunningPolicyId> policyIdByObligationType) {
    return new DefaultPolicyResolver(registry, policyIdByObligationType);
  }
}
