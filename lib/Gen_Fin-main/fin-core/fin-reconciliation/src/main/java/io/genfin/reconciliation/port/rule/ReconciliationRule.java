package io.genfin.reconciliation.port.rule;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/**
 * One reconciliation check over two sides of a comparison — e.g. amounts equal, currencies equal,
 * duplicate payment. Returns an empty list when nothing is wrong; a {@link RuleEngine} composes
 * many of these and collects every {@link RuleResult} instead of stopping at the first one found.
 */
public interface ReconciliationRule extends Extension {

  List<RuleResult> evaluate(ComparisonRecord left, ComparisonRecord right, RuleContext context);
}
