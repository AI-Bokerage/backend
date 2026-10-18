package io.genfin.dunning.rule;

import io.genfin.dunning.internal.rule.DefaultCollectionRuleEngine;
import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.port.rule.CollectionRuleEngine;
import java.util.List;

/** Factory for {@link CollectionRuleEngine} instances. */
public final class CollectionRuleEngines {

  private CollectionRuleEngines() {}

  /** An engine carrying every {@link CollectionRules#defaultRules()}. */
  public static CollectionRuleEngine standard() {
    return new DefaultCollectionRuleEngine(CollectionRules.defaultRules());
  }

  public static CollectionRuleEngine of(List<CollectionRule> rules) {
    return new DefaultCollectionRuleEngine(rules);
  }
}
