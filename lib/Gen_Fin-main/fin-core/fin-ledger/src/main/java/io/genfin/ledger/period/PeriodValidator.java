package io.genfin.ledger.period;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.exception.Severity;
import io.genfin.api.validation.Validate;
import java.util.ArrayList;
import java.util.List;

/**
 * Structural guardrails around {@link AccountingPeriod}s that hold regardless of fiscal calendar: a
 * fact may only post into a period that is both open and actually covers its date, and a set of
 * periods a deployment intends to use together must not overlap. Never re-derives the calendar
 * itself - that is entirely the {@link io.genfin.ledger.port.period.PeriodPolicy}'s job.
 */
public final class PeriodValidator {

  /** Whether {@code postingDate} may be posted into {@code period}: open, and within its bounds. */
  public PeriodValidationResult validatePosting(AccountingPeriod period, OccurredAt postingDate) {
    Validate.notNull(period, "period must not be null.");
    Validate.notNull(postingDate, "postingDate must not be null.");
    List<PeriodIssue> issues = new ArrayList<>();
    if (!period.isOpen()) {
      issues.add(
          PeriodIssue.of(
              "PERIOD_NOT_OPEN",
              "period " + period.id().value() + " is " + period.status().code() + ", not OPEN.",
              Severity.CRITICAL));
    }
    if (!period.contains(postingDate)) {
      issues.add(
          PeriodIssue.of(
              "POSTING_DATE_OUTSIDE_PERIOD",
              "posting date "
                  + postingDate.value()
                  + " falls outside period "
                  + period.id().value()
                  + ".",
              Severity.CRITICAL));
    }
    return new PeriodValidationResult(issues);
  }

  /** Whether any two periods in {@code periods} overlap in time. */
  public PeriodValidationResult validateNoOverlaps(List<AccountingPeriod> periods) {
    Validate.notNull(periods, "periods must not be null.");
    List<PeriodIssue> issues = new ArrayList<>();
    for (int i = 0; i < periods.size(); i++) {
      for (int j = i + 1; j < periods.size(); j++) {
        AccountingPeriod first = periods.get(i);
        AccountingPeriod second = periods.get(j);
        if (overlaps(first, second)) {
          issues.add(
              PeriodIssue.of(
                  "OVERLAPPING_PERIODS",
                  "period " + first.id().value() + " overlaps period " + second.id().value() + ".",
                  Severity.CRITICAL));
        }
      }
    }
    return new PeriodValidationResult(issues);
  }

  private boolean overlaps(AccountingPeriod first, AccountingPeriod second) {
    return first.start().value().isBefore(second.end().value())
        && second.start().value().isBefore(first.end().value());
  }
}
