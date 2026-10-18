package io.genfin.dunning.port.rule;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.rule.CollectionDecision;
import io.genfin.dunning.rule.CollectionRuleContext;
import java.util.List;

/**
 * One collection-gating check over a {@link CollectionRuleContext} - e.g. maximum retries reached,
 * grace period active, collection paused, holiday-skip-today, priority handling. Returns an empty
 * list when the rule finds nothing to report; a {@link CollectionRuleEngine} composes many of these
 * and collects every {@link CollectionDecision} instead of stopping at the first one found. Decides
 * only - a rule never sends a reminder, retries a payment, or performs any action itself.
 */
@FunctionalInterface
public interface CollectionRule extends Extension {

  List<CollectionDecision> evaluate(CollectionRuleContext context);
}
