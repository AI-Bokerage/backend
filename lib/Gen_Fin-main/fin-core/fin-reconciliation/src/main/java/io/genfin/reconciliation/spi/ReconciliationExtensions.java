package io.genfin.reconciliation.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.reconciliation.calculation.DifferenceCalculators;
import io.genfin.reconciliation.calculation.SummaryCalculators;
import io.genfin.reconciliation.calculation.VarianceCalculators;
import io.genfin.reconciliation.comparison.ComparisonPolicies;
import io.genfin.reconciliation.comparison.ComparisonStrategies;
import io.genfin.reconciliation.discrepancy.DiscrepancyDetectors;
import io.genfin.reconciliation.discrepancy.DiscrepancyPolicies;
import io.genfin.reconciliation.discrepancy.DiscrepancyReasonRegistries;
import io.genfin.reconciliation.lifecycle.ReconciliationLifecycles;
import io.genfin.reconciliation.matching.MatchingEngines;
import io.genfin.reconciliation.matching.MatchingPolicies;
import io.genfin.reconciliation.matching.MatchingStrategies;
import io.genfin.reconciliation.port.calculation.DifferenceCalculator;
import io.genfin.reconciliation.port.calculation.SummaryCalculator;
import io.genfin.reconciliation.port.calculation.VarianceCalculator;
import io.genfin.reconciliation.port.comparison.ComparisonPolicy;
import io.genfin.reconciliation.port.comparison.ComparisonStrategy;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyDetector;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyPolicy;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyReasonProvider;
import io.genfin.reconciliation.port.lifecycle.ReconciliationLifecycleProvider;
import io.genfin.reconciliation.port.matching.MatchingEngine;
import io.genfin.reconciliation.port.matching.MatchingPolicy;
import io.genfin.reconciliation.port.matching.MatchingStrategy;
import io.genfin.reconciliation.port.report.ReportFormatter;
import io.genfin.reconciliation.port.rule.ReconciliationRule;
import io.genfin.reconciliation.port.rule.RuleEngine;
import io.genfin.reconciliation.port.tolerance.ToleranceCalculator;
import io.genfin.reconciliation.port.validation.ReconciliationValidator;
import io.genfin.reconciliation.report.ReportFormatters;
import io.genfin.reconciliation.rule.ReconciliationRules;
import io.genfin.reconciliation.rule.RuleEngines;
import io.genfin.reconciliation.tolerance.ToleranceCalculators;
import io.genfin.reconciliation.validation.Validators;

/**
 * Registers every default Reconciliation-engine extension so downstream code discovers them through
 * one mechanism.
 */
public final class ReconciliationExtensions {

  private ReconciliationExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(ReconciliationLifecycleProvider.class, ReconciliationLifecycles.standard());
    registerMatchingStrategies(registry);
    registry.register(MatchingPolicy.class, MatchingPolicies.standard());
    registry.register(MatchingEngine.class, MatchingEngines.standard());
    registerComparisonStrategies(registry);
    registry.register(ComparisonPolicy.class, ComparisonPolicies.standard());
    registry.register(
        DiscrepancyReasonProvider.class, DiscrepancyReasonRegistries.standardCatalog());
    registry.register(DiscrepancyPolicy.class, DiscrepancyPolicies.standard());
    registry.register(DiscrepancyDetector.class, DiscrepancyDetectors.standard());
    registry.register(ToleranceCalculator.class, ToleranceCalculators.standard());
    registerRules(registry);
    registry.register(RuleEngine.class, RuleEngines.standard());
    registry.register(SummaryCalculator.class, SummaryCalculators.standard());
    registry.register(DifferenceCalculator.class, DifferenceCalculators.standard());
    registry.register(VarianceCalculator.class, VarianceCalculators.standard());
    registry.register(ReportFormatter.class, ReportFormatters.standard());
    registry.register(ReconciliationValidator.class, Validators.standard());
  }

  private static void registerRules(ExtensionRegistry registry) {
    ReconciliationRules.defaultRules()
        .forEach(rule -> registry.register(ReconciliationRule.class, rule));
  }

  private static void registerMatchingStrategies(ExtensionRegistry registry) {
    MatchingStrategies.standardChain()
        .forEach(strategy -> registry.register(MatchingStrategy.class, strategy));
  }

  private static void registerComparisonStrategies(ExtensionRegistry registry) {
    ComparisonStrategies.standardChain()
        .forEach(strategy -> registry.register(ComparisonStrategy.class, strategy));
  }
}
