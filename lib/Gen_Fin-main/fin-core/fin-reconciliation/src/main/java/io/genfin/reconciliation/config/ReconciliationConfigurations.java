package io.genfin.reconciliation.config;

import io.genfin.reconciliation.calculation.SummaryCalculators;
import io.genfin.reconciliation.comparison.ComparisonPolicies;
import io.genfin.reconciliation.discrepancy.DiscrepancyDetectors;
import io.genfin.reconciliation.discrepancy.DiscrepancyPolicies;
import io.genfin.reconciliation.lifecycle.ReconciliationLifecycles;
import io.genfin.reconciliation.matching.MatchingEngines;
import io.genfin.reconciliation.matching.MatchingPolicies;
import io.genfin.reconciliation.matching.MatchingStrategies;
import io.genfin.reconciliation.port.tolerance.CustomToleranceRuleRegistry;
import io.genfin.reconciliation.report.ReportFormatters;
import io.genfin.reconciliation.rule.ReconciliationRules;
import io.genfin.reconciliation.rule.RuleEngines;
import io.genfin.reconciliation.tolerance.ToleranceCalculators;

/**
 * Factory for the default {@link ReconciliationConfiguration}, mirroring {@code
 * RefundConfigurations}.
 */
public final class ReconciliationConfigurations {

  private ReconciliationConfigurations() {}

  public static ReconciliationConfiguration standard() {
    CustomToleranceRuleRegistry customRules = ToleranceCalculators.newCustomRuleRegistry();
    return ReconciliationConfiguration.builder()
        .lifecycleProvider(ReconciliationLifecycles.standard())
        .comparisonPolicy(ComparisonPolicies.standard())
        .discrepancyDetector(DiscrepancyDetectors.standard())
        .discrepancyPolicy(DiscrepancyPolicies.standard())
        .toleranceConfiguration(
            ToleranceConfiguration.builder()
                .calculator(ToleranceCalculators.of(customRules))
                .customRules(customRules)
                .build())
        .matchingConfiguration(
            MatchingConfiguration.builder()
                .engine(MatchingEngines.standard())
                .policy(MatchingPolicies.standard())
                .strategies(MatchingStrategies.standardChain())
                .build())
        .ruleConfiguration(
            RuleConfiguration.builder()
                .engine(RuleEngines.standard())
                .rules(ReconciliationRules.defaultRules())
                .build())
        .reportingConfiguration(
            ReportingConfiguration.builder()
                .summaryCalculator(SummaryCalculators.standard())
                .formatter(ReportFormatters.standard())
                .build())
        .build();
  }
}
