package io.genfin.dunning.policy;

import io.genfin.dunning.internal.policy.DefaultPolicyRegistry;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.policy.PolicyRegistry;
import java.util.List;

/**
 * Factory for {@link PolicyRegistry} instances. Deliberately has no pre-populated catalog - Gen-Fin
 * defines no built-in dunning policies, so every registry starts empty until an application
 * registers its own.
 */
public final class PolicyRegistries {

  private PolicyRegistries() {}

  public static PolicyRegistry empty() {
    return new DefaultPolicyRegistry();
  }

  public static PolicyRegistry of(List<DunningPolicy> policies) {
    PolicyRegistry registry = empty();
    policies.forEach(registry::register);
    return registry;
  }
}
