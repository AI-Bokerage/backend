package io.genfin.dunning.internal.policy;

import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.policy.PolicyRegistry;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class DefaultPolicyRegistry implements PolicyRegistry {

  private final ConcurrentMap<String, DunningPolicy> policies = new ConcurrentHashMap<>();

  @Override
  public void register(DunningPolicy policy) {
    policies.put(policy.id().value(), policy);
  }

  @Override
  public Optional<DunningPolicy> find(DunningPolicyId id) {
    return Optional.ofNullable(policies.get(id.value()));
  }

  @Override
  public List<DunningPolicy> findAll() {
    return List.copyOf(policies.values());
  }
}
