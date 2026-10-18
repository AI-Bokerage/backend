package io.genfin.dunning.internal.escalation;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.escalation.EscalationRule;
import io.genfin.dunning.port.escalation.EscalationPolicy;
import java.util.List;

/** Adapts a resolved list of {@link EscalationRule}s to the {@link EscalationPolicy} port. */
public final class DefaultEscalationPolicy implements EscalationPolicy {

  private final List<EscalationRule> escalationRules;

  public DefaultEscalationPolicy(List<EscalationRule> escalationRules) {
    this.escalationRules =
        List.copyOf(Validate.notNull(escalationRules, "escalationRules must not be null."));
  }

  @Override
  public List<EscalationRule> escalationRules() {
    return escalationRules;
  }
}
