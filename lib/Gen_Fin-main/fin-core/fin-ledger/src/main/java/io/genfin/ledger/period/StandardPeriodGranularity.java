package io.genfin.ledger.period;

/** The standard period granularities every {@code PeriodPolicy} is expected to support. */
public enum StandardPeriodGranularity implements PeriodGranularity {
  DAILY,
  MONTHLY,
  QUARTERLY,
  YEARLY;

  @Override
  public String code() {
    return name();
  }
}
