package io.genfin.reconciliation.config;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.port.comparison.ComparisonPolicy;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyDetector;
import io.genfin.reconciliation.port.discrepancy.DiscrepancyPolicy;
import io.genfin.reconciliation.port.lifecycle.ReconciliationLifecycleProvider;

/**
 * The full, explicit policy set for a Reconciliation-engine deployment, mirroring {@code
 * RefundConfiguration}: the cross-cutting policies (lifecycle, comparison, discrepancy) plus the
 * three composed sub-configurations for the concerns that carry more than one collaborator each.
 */
public final class ReconciliationConfiguration {

  private final ReconciliationLifecycleProvider lifecycleProvider;
  private final ComparisonPolicy comparisonPolicy;
  private final DiscrepancyDetector discrepancyDetector;
  private final DiscrepancyPolicy discrepancyPolicy;
  private final ToleranceConfiguration toleranceConfiguration;
  private final MatchingConfiguration matchingConfiguration;
  private final RuleConfiguration ruleConfiguration;
  private final ReportingConfiguration reportingConfiguration;

  private ReconciliationConfiguration(Builder builder) {
    this.lifecycleProvider =
        Validate.notNull(builder.lifecycleProvider, "lifecycleProvider must not be null.");
    this.comparisonPolicy =
        Validate.notNull(builder.comparisonPolicy, "comparisonPolicy must not be null.");
    this.discrepancyDetector =
        Validate.notNull(builder.discrepancyDetector, "discrepancyDetector must not be null.");
    this.discrepancyPolicy =
        Validate.notNull(builder.discrepancyPolicy, "discrepancyPolicy must not be null.");
    this.toleranceConfiguration =
        Validate.notNull(
            builder.toleranceConfiguration, "toleranceConfiguration must not be null.");
    this.matchingConfiguration =
        Validate.notNull(builder.matchingConfiguration, "matchingConfiguration must not be null.");
    this.ruleConfiguration =
        Validate.notNull(builder.ruleConfiguration, "ruleConfiguration must not be null.");
    this.reportingConfiguration =
        Validate.notNull(
            builder.reportingConfiguration, "reportingConfiguration must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public ReconciliationLifecycleProvider lifecycleProvider() {
    return lifecycleProvider;
  }

  public ComparisonPolicy comparisonPolicy() {
    return comparisonPolicy;
  }

  public DiscrepancyDetector discrepancyDetector() {
    return discrepancyDetector;
  }

  public DiscrepancyPolicy discrepancyPolicy() {
    return discrepancyPolicy;
  }

  public ToleranceConfiguration toleranceConfiguration() {
    return toleranceConfiguration;
  }

  public MatchingConfiguration matchingConfiguration() {
    return matchingConfiguration;
  }

  public RuleConfiguration ruleConfiguration() {
    return ruleConfiguration;
  }

  public ReportingConfiguration reportingConfiguration() {
    return reportingConfiguration;
  }

  public static final class Builder {

    private ReconciliationLifecycleProvider lifecycleProvider;
    private ComparisonPolicy comparisonPolicy;
    private DiscrepancyDetector discrepancyDetector;
    private DiscrepancyPolicy discrepancyPolicy;
    private ToleranceConfiguration toleranceConfiguration;
    private MatchingConfiguration matchingConfiguration;
    private RuleConfiguration ruleConfiguration;
    private ReportingConfiguration reportingConfiguration;

    public Builder lifecycleProvider(ReconciliationLifecycleProvider lifecycleProvider) {
      this.lifecycleProvider = lifecycleProvider;
      return this;
    }

    public Builder comparisonPolicy(ComparisonPolicy comparisonPolicy) {
      this.comparisonPolicy = comparisonPolicy;
      return this;
    }

    public Builder discrepancyDetector(DiscrepancyDetector discrepancyDetector) {
      this.discrepancyDetector = discrepancyDetector;
      return this;
    }

    public Builder discrepancyPolicy(DiscrepancyPolicy discrepancyPolicy) {
      this.discrepancyPolicy = discrepancyPolicy;
      return this;
    }

    public Builder toleranceConfiguration(ToleranceConfiguration toleranceConfiguration) {
      this.toleranceConfiguration = toleranceConfiguration;
      return this;
    }

    public Builder matchingConfiguration(MatchingConfiguration matchingConfiguration) {
      this.matchingConfiguration = matchingConfiguration;
      return this;
    }

    public Builder ruleConfiguration(RuleConfiguration ruleConfiguration) {
      this.ruleConfiguration = ruleConfiguration;
      return this;
    }

    public Builder reportingConfiguration(ReportingConfiguration reportingConfiguration) {
      this.reportingConfiguration = reportingConfiguration;
      return this;
    }

    public ReconciliationConfiguration build() {
      return new ReconciliationConfiguration(this);
    }
  }
}
