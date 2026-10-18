package io.genfin.reconciliation.port.rule;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.comparison.ComparisonRecord;
import io.genfin.reconciliation.rule.RuleContext;
import io.genfin.reconciliation.rule.RuleResult;
import java.util.List;

/**
 * Runs every configured {@link ReconciliationRule} over two sides of a comparison and collects all
 * {@link RuleResult}s, never short-circuiting on the first rule that finds a problem.
 */
public interface RuleEngine extends Extension {

  List<RuleResult> evaluate(ComparisonRecord left, ComparisonRecord right, RuleContext context);
}
