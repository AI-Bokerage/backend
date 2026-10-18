package io.genfin.ledger.internal.period;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.exception.UnsupportedCapabilityException;
import io.genfin.api.validation.Validate;
import io.genfin.ledger.period.FiscalMonth;
import io.genfin.ledger.period.FiscalQuarter;
import io.genfin.ledger.period.FiscalYear;
import io.genfin.ledger.period.PeriodGranularity;
import io.genfin.ledger.period.StandardPeriodGranularity;
import io.genfin.ledger.port.period.PeriodPolicy;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.function.Function;

/**
 * The standard {@link PeriodPolicy}: a fiscal year starting on the first day of a configurable
 * month (January by default, i.e. an ordinary calendar year), split into ordinary 3-month quarters
 * and calendar months. This is one fiscal calendar choice, not the only one - a deployment with a
 * different fiscal calendar (a 4-4-5 retail calendar, weekly periods, ...) registers its own {@link
 * PeriodPolicy} instead of extending this one.
 *
 * <p>ponytail: day boundaries are computed in UTC (an {@link OccurredAt} carries no zone). A
 * deployment whose fiscal day boundary is a local business-day cutover should supply a policy that
 * converts to its own zone before truncating.
 */
public final class DefaultPeriodPolicy implements PeriodPolicy {

  private static final int MONTHS_PER_QUARTER = 3;
  private static final int MIN_MONTH = 1;
  private static final int MAX_MONTH = 12;

  private final int fiscalYearStartMonth;
  private final Map<String, Function<LocalDate, LocalDate[]>> boundsFunctions;

  public DefaultPeriodPolicy(int fiscalYearStartMonth) {
    Validate.argument(
        fiscalYearStartMonth >= MIN_MONTH && fiscalYearStartMonth <= MAX_MONTH,
        "fiscalYearStartMonth must be between 1 and 12.");
    this.fiscalYearStartMonth = fiscalYearStartMonth;
    this.boundsFunctions =
        Map.of(
            StandardPeriodGranularity.DAILY.code(),
            date -> new LocalDate[] {date, date.plusDays(1)},
            StandardPeriodGranularity.MONTHLY.code(),
            date -> {
              LocalDate start = date.withDayOfMonth(1);
              return new LocalDate[] {start, start.plusMonths(1)};
            },
            StandardPeriodGranularity.QUARTERLY.code(),
            this::quarterBounds,
            StandardPeriodGranularity.YEARLY.code(),
            this::yearBounds);
  }

  @Override
  public FiscalYear fiscalYearOf(OccurredAt instant) {
    Validate.notNull(instant, "instant must not be null.");
    LocalDate[] bounds = yearBounds(toDate(instant));
    return new FiscalYear(yearLabel(bounds[0]), toInstant(bounds[0]), toInstant(bounds[1]));
  }

  @Override
  public FiscalQuarter fiscalQuarterOf(OccurredAt instant) {
    Validate.notNull(instant, "instant must not be null.");
    LocalDate date = toDate(instant);
    FiscalYear fiscalYear = fiscalYearOf(instant);
    LocalDate[] bounds = quarterBounds(date);
    int quarterNumber = (monthsSince(toDate(fiscalYear.start()), date) / MONTHS_PER_QUARTER) + 1;
    return new FiscalQuarter(fiscalYear, quarterNumber, toInstant(bounds[0]), toInstant(bounds[1]));
  }

  @Override
  public FiscalMonth fiscalMonthOf(OccurredAt instant) {
    Validate.notNull(instant, "instant must not be null.");
    LocalDate date = toDate(instant);
    FiscalYear fiscalYear = fiscalYearOf(instant);
    LocalDate start = date.withDayOfMonth(1);
    int monthNumber = monthsSince(toDate(fiscalYear.start()), date) + 1;
    return new FiscalMonth(
        fiscalYear, monthNumber, toInstant(start), toInstant(start.plusMonths(1)));
  }

  @Override
  public OccurredAt periodStart(OccurredAt instant, PeriodGranularity granularity) {
    return toInstant(boundsOf(instant, granularity)[0]);
  }

  @Override
  public OccurredAt periodEnd(OccurredAt instant, PeriodGranularity granularity) {
    return toInstant(boundsOf(instant, granularity)[1]);
  }

  private LocalDate[] boundsOf(OccurredAt instant, PeriodGranularity granularity) {
    Validate.notNull(instant, "instant must not be null.");
    Validate.notNull(granularity, "granularity must not be null.");
    Function<LocalDate, LocalDate[]> function = boundsFunctions.get(granularity.code());
    if (function == null) {
      throw new UnsupportedCapabilityException(
          "no period boundaries known for granularity "
              + granularity.code()
              + "; register a PeriodPolicy that supports it.");
    }
    return function.apply(toDate(instant));
  }

  private LocalDate[] yearBounds(LocalDate date) {
    int startYear =
        date.getMonthValue() >= fiscalYearStartMonth ? date.getYear() : date.getYear() - 1;
    LocalDate start = LocalDate.of(startYear, fiscalYearStartMonth, 1);
    return new LocalDate[] {start, start.plusYears(1)};
  }

  private LocalDate[] quarterBounds(LocalDate date) {
    LocalDate fiscalYearStart = yearBounds(date)[0];
    int quarterIndex = monthsSince(fiscalYearStart, date) / MONTHS_PER_QUARTER;
    LocalDate start = fiscalYearStart.plusMonths((long) quarterIndex * MONTHS_PER_QUARTER);
    return new LocalDate[] {start, start.plusMonths(MONTHS_PER_QUARTER)};
  }

  private int monthsSince(LocalDate from, LocalDate to) {
    return ((to.getYear() - from.getYear()) * MAX_MONTH)
        + (to.getMonthValue() - from.getMonthValue());
  }

  private String yearLabel(LocalDate fiscalYearStart) {
    return "FY" + fiscalYearStart.getYear();
  }

  private LocalDate toDate(OccurredAt instant) {
    return instant.value().atZone(ZoneOffset.UTC).toLocalDate();
  }

  private OccurredAt toInstant(LocalDate date) {
    return new OccurredAt(date.atStartOfDay(ZoneOffset.UTC).toInstant());
  }
}
