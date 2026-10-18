package io.genfin.dunning.port.escalation;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.escalation.EscalationRule;
import java.util.List;

/**
 * The full, explicit policy governing how the Escalation Engine decides whether an obligation's
 * failed-attempt count warrants escalation: the ladder of {@link EscalationRule}s an application
 * resolves from its own {@code DunningPolicy}. fin-dunning never hardcodes a threshold, level, or
 * action anywhere in default/example code paths.
 */
public interface EscalationPolicy extends Extension {

  List<EscalationRule> escalationRules();
}
