package io.genfin.ledger.config;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.report.ReportFormatter;
import io.genfin.ledger.port.trialbalance.TrialBalanceCalculator;

/** The trial-balance and reporting policy set for a Ledger-engine deployment. */
public final class ReportingConfiguration {

  private final TrialBalanceCalculator trialBalanceCalculator;
  private final ReportFormatter formatter;

  private ReportingConfiguration(Builder builder) {
    this.trialBalanceCalculator =
        Validate.notNull(
            builder.trialBalanceCalculator, "trialBalanceCalculator must not be null.");
    this.formatter = Validate.notNull(builder.formatter, "formatter must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public TrialBalanceCalculator trialBalanceCalculator() {
    return trialBalanceCalculator;
  }

  public ReportFormatter formatter() {
    return formatter;
  }

  public static final class Builder {

    private TrialBalanceCalculator trialBalanceCalculator;
    private ReportFormatter formatter;

    public Builder trialBalanceCalculator(TrialBalanceCalculator trialBalanceCalculator) {
      this.trialBalanceCalculator = trialBalanceCalculator;
      return this;
    }

    public Builder formatter(ReportFormatter formatter) {
      this.formatter = formatter;
      return this;
    }

    public ReportingConfiguration build() {
      return new ReportingConfiguration(this);
    }
  }
}
