package io.genfin.reconciliation.config;

import io.genfin.api.validation.Validate;
import io.genfin.reconciliation.port.calculation.SummaryCalculator;
import io.genfin.reconciliation.port.report.ReportFormatter;

/** The summarization and reporting policy set for a Reconciliation-engine deployment. */
public final class ReportingConfiguration {

  private final SummaryCalculator summaryCalculator;
  private final ReportFormatter formatter;

  private ReportingConfiguration(Builder builder) {
    this.summaryCalculator =
        Validate.notNull(builder.summaryCalculator, "summaryCalculator must not be null.");
    this.formatter = Validate.notNull(builder.formatter, "formatter must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public SummaryCalculator summaryCalculator() {
    return summaryCalculator;
  }

  public ReportFormatter formatter() {
    return formatter;
  }

  public static final class Builder {

    private SummaryCalculator summaryCalculator;
    private ReportFormatter formatter;

    public Builder summaryCalculator(SummaryCalculator summaryCalculator) {
      this.summaryCalculator = summaryCalculator;
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
