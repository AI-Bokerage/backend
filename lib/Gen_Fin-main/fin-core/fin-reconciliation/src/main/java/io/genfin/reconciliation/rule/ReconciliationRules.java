package io.genfin.reconciliation.rule;

import io.genfin.reconciliation.internal.rule.AmountsEqualRule;
import io.genfin.reconciliation.internal.rule.AmountsWithinToleranceRule;
import io.genfin.reconciliation.internal.rule.CurrenciesEqualRule;
import io.genfin.reconciliation.internal.rule.DuplicatePaymentRule;
import io.genfin.reconciliation.internal.rule.DuplicateRefundRule;
import io.genfin.reconciliation.internal.rule.ExpiredTransactionRule;
import io.genfin.reconciliation.internal.rule.MissingPaymentRule;
import io.genfin.reconciliation.internal.rule.MissingSettlementRule;
import io.genfin.reconciliation.internal.rule.ReferencesEqualRule;
import io.genfin.reconciliation.internal.rule.RefundExistsRule;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import java.util.List;

/** Factory for the default {@link ReconciliationRule}s, mirroring {@code MatchingStrategies}. */
public final class ReconciliationRules {

  private ReconciliationRules() {}

  /** Every default rule, in the order the standard {@code RuleEngine} runs them. */
  public static List<ReconciliationRule> defaultRules() {
    return List.of(
        new AmountsEqualRule(),
        new AmountsWithinToleranceRule(),
        new CurrenciesEqualRule(),
        new ReferencesEqualRule(),
        new RefundExistsRule(),
        new DuplicatePaymentRule(),
        new DuplicateRefundRule(),
        new MissingSettlementRule(),
        new MissingPaymentRule(),
        new ExpiredTransactionRule());
  }
}
