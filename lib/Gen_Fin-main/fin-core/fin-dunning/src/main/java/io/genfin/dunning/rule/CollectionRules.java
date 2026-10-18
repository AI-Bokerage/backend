package io.genfin.dunning.rule;

import io.genfin.dunning.internal.rule.CollectionPausedRule;
import io.genfin.dunning.internal.rule.GracePeriodActiveRule;
import io.genfin.dunning.internal.rule.HolidaySkipTodayRule;
import io.genfin.dunning.internal.rule.MaxRetriesReachedRule;
import io.genfin.dunning.internal.rule.PriorityCollectionRule;
import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.port.rule.CollectionRuleProvider;
import io.genfin.money.money.Money;
import java.util.List;

/**
 * Factory for illustrative example {@link CollectionRule}s - maximum retries reached, grace period
 * active, collection paused, holiday-skip-today, and priority handling. None of these are mandatory
 * or exhaustive; fin-dunning ships them only as registrable examples an application may include,
 * omit, or replace entirely with its own via a {@link CollectionRuleProvider}. Mirrors {@code
 * io.genfin.reconciliation.rule.ReconciliationRules}.
 */
public final class CollectionRules {

  private CollectionRules() {}

  /**
   * The example rules that need no application-supplied configuration to construct: maximum retries
   * reached, grace period active, collection paused, and holiday-skip-today.
   */
  public static List<CollectionRule> defaultRules() {
    return List.of(
        new MaxRetriesReachedRule(),
        new GracePeriodActiveRule(),
        new CollectionPausedRule(),
        new HolidaySkipTodayRule());
  }

  /**
   * Example priority-handling rule: flags an obligation once its amount reaches {@code threshold}.
   * Deliberately not part of {@link #defaultRules()} - unlike the other examples, what counts as a
   * priority amount is always an application-specific number, so this rule is only ever constructed
   * explicitly with the caller's own threshold.
   */
  public static CollectionRule priorityAbove(Money threshold) {
    return new PriorityCollectionRule(threshold);
  }
}
