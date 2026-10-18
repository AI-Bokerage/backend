package io.genfin.dunning.port.rule;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.rule.CollectionDecision;
import io.genfin.dunning.rule.CollectionRuleContext;
import java.util.List;

/**
 * Runs every configured {@link CollectionRule} over a {@link CollectionRuleContext} and collects
 * all {@link CollectionDecision}s, never short-circuiting on the first rule that finds something to
 * report - mirroring {@code io.genfin.reconciliation.port.rule.RuleEngine}.
 */
public interface CollectionRuleEngine extends Extension {

  List<CollectionDecision> evaluate(CollectionRuleContext context);
}
