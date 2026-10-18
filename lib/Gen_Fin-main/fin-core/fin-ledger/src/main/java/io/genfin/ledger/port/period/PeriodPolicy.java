package io.genfin.ledger.port.period;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.port.spi.Extension;
import io.genfin.ledger.period.FiscalMonth;
import io.genfin.ledger.period.FiscalQuarter;
import io.genfin.ledger.period.FiscalYear;
import io.genfin.ledger.period.PeriodGranularity;

/**
 * The fiscal calendar itself: which {@link FiscalYear}/{@link FiscalQuarter}/{@link FiscalMonth} an
 * instant falls in, and where a {@link PeriodGranularity} bucket starts and ends around it. Gen-
 * Fin hardcodes no single fiscal calendar - a January-start calendar year is only the {@code
 * DefaultPeriodPolicy}'s choice, not the only one; a deployment with a non-calendar fiscal year (a
 * 4-4-5 retail calendar, an April-start fiscal year, weekly buckets, ...) registers its own
 * implementation instead, exactly as {@code io.genfin.reconciliation.port.matching.MatchingPolicy}
 * lets a deployment replace matching rules wholesale rather than adding cases to a fixed one.
 */
public interface PeriodPolicy extends Extension {

  FiscalYear fiscalYearOf(OccurredAt instant);

  FiscalQuarter fiscalQuarterOf(OccurredAt instant);

  FiscalMonth fiscalMonthOf(OccurredAt instant);

  /** The inclusive start of the {@code granularity} bucket containing {@code instant}. */
  OccurredAt periodStart(OccurredAt instant, PeriodGranularity granularity);

  /** The exclusive end of the {@code granularity} bucket containing {@code instant}. */
  OccurredAt periodEnd(OccurredAt instant, PeriodGranularity granularity);
}
