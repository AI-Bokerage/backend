package io.genfin.dunning.internal.policy;

import io.genfin.api.port.spi.Resolver;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.obligation.ObligationType;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.policy.PolicyRegistry;
import io.genfin.dunning.port.policy.PolicyResolver;
import java.util.Optional;

/**
 * Resolves a policy by delegating which {@link DunningPolicyId} applies to an obligation's {@link
 * ObligationType} to an application-supplied {@link Resolver}, then looking that id up in the
 * {@link PolicyRegistry}. The obligation-type-to-policy mapping is never fin-dunning's decision.
 */
public final class DefaultPolicyResolver implements PolicyResolver {

  private final PolicyRegistry registry;
  private final Resolver<ObligationType, DunningPolicyId> policyIdByObligationType;

  public DefaultPolicyResolver(
      PolicyRegistry registry, Resolver<ObligationType, DunningPolicyId> policyIdByObligationType) {
    this.registry = Validate.notNull(registry, "registry must not be null.");
    this.policyIdByObligationType =
        Validate.notNull(policyIdByObligationType, "policyIdByObligationType must not be null.");
  }

  @Override
  public Optional<DunningPolicy> resolve(FinancialObligation obligation) {
    Validate.notNull(obligation, "obligation must not be null.");
    return policyIdByObligationType.resolve(obligation.type()).flatMap(registry::find);
  }
}
