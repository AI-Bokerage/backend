package io.genfin.dunning.port.policy;

import io.genfin.api.exception.ValidationException;
import io.genfin.dunning.id.DunningPolicyId;
import java.util.List;
import java.util.Optional;

/**
 * Catalog of the {@link DunningPolicy}s an application has registered - e.g. "Policy A: 1/3/5 days
 * then suspend", "Policy B: 6/12/24 hours then escalate/write-off", "Policy C: every weekday, skip
 * weekends, max 8 retries, never suspend". fin-dunning ships none of its own; a {@link
 * PolicyResolver} looks policies up here once it has decided which one applies to a given
 * obligation. Mirrors {@code io.genfin.ledger.port.account.AccountTypeRegistry}.
 */
public interface PolicyRegistry {

  void register(DunningPolicy policy);

  Optional<DunningPolicy> find(DunningPolicyId id);

  default DunningPolicy require(DunningPolicyId id) {
    return find(id)
        .orElseThrow(() -> new ValidationException("Unregistered dunning policy: " + id.value()));
  }

  List<DunningPolicy> findAll();
}
