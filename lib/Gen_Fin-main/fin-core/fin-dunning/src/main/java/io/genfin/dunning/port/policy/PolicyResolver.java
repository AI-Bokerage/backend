package io.genfin.dunning.port.policy;

import io.genfin.api.exception.ValidationException;
import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.obligation.FinancialObligation;
import java.util.Optional;

/**
 * Resolves which registered {@link DunningPolicy} applies to a given {@link FinancialObligation}.
 * Which obligation types, references, or amounts map to which policy is entirely an application
 * decision - fin-dunning never hardcodes that mapping, only this extension point plus a default
 * implementation (see {@code io.genfin.dunning.policy.PolicyResolvers}) an application wires with
 * its own obligation-type-to-policy mapping.
 */
public interface PolicyResolver extends Extension {

  Optional<DunningPolicy> resolve(FinancialObligation obligation);

  default DunningPolicy require(FinancialObligation obligation) {
    return resolve(obligation)
        .orElseThrow(
            () ->
                new ValidationException(
                    "No dunning policy resolved for obligation: " + obligation.id().value()));
  }
}
