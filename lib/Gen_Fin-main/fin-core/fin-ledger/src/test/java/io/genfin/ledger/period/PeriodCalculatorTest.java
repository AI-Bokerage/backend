package io.genfin.ledger.period;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.event.OccurredAt;
import io.genfin.api.exception.StateTransitionException;
import io.genfin.ledger.port.period.PeriodCalculator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PeriodCalculatorTest {

  private static final OccurredAt MID_MARCH = new OccurredAt(Instant.parse("2026-03-15T00:00:00Z"));

  private static PeriodCalculator standardCalculator() {
    return PeriodCalculators.of(PeriodCalculators.standardPolicy());
  }

  @Test
  void buildsACalendarMonthPeriod() {
    AccountingPeriod march =
        standardCalculator().periodFor(MID_MARCH, StandardPeriodGranularity.MONTHLY);

    assertThat(march.start().value()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
    assertThat(march.end().value()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    assertThat(march.status()).isEqualTo(StandardPeriodStatus.OPEN);
    assertThat(march.contains(MID_MARCH)).isTrue();
  }

  @Test
  void buildsACalendarYearPeriodAndItsFirstQuarter() {
    PeriodCalculator calculator = standardCalculator();
    AccountingPeriod year = calculator.periodFor(MID_MARCH, StandardPeriodGranularity.YEARLY);
    AccountingPeriod quarter = calculator.periodFor(MID_MARCH, StandardPeriodGranularity.QUARTERLY);

    assertThat(year.start().value()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(year.end().value()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
    assertThat(quarter.start().value()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(quarter.end().value()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
  }

  @Test
  void nextAndPreviousStepByOneGranularityBucket() {
    PeriodCalculator calculator = standardCalculator();
    AccountingPeriod march = calculator.periodFor(MID_MARCH, StandardPeriodGranularity.MONTHLY);

    AccountingPeriod april = calculator.next(march);
    AccountingPeriod february = calculator.previous(march);

    assertThat(april.start().value()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    assertThat(february.start().value()).isEqualTo(Instant.parse("2026-02-01T00:00:00Z"));
  }

  @Test
  void periodsBetweenCoversTheWholeRangeConsecutively() {
    PeriodCalculator calculator = standardCalculator();
    OccurredAt start = new OccurredAt(Instant.parse("2026-01-15T00:00:00Z"));
    OccurredAt end = new OccurredAt(Instant.parse("2026-03-10T00:00:00Z"));

    List<AccountingPeriod> months =
        calculator.periodsBetween(start, end, StandardPeriodGranularity.MONTHLY);

    assertThat(months).hasSize(3);
    assertThat(months.get(0).start().value()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(months.get(2).end().value()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
  }

  @Test
  void customFiscalYearStartMonthShiftsYearAndQuarterBoundaries() {
    PeriodCalculator calculator = PeriodCalculators.of(PeriodCalculators.calendarPolicy(4));

    AccountingPeriod year = calculator.periodFor(MID_MARCH, StandardPeriodGranularity.YEARLY);
    AccountingPeriod quarter = calculator.periodFor(MID_MARCH, StandardPeriodGranularity.QUARTERLY);

    assertThat(year.start().value()).isEqualTo(Instant.parse("2025-04-01T00:00:00Z"));
    assertThat(year.end().value()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    assertThat(quarter.start().value()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void periodTransitionsFollowTheOpenClosedLockedArchivedLifecycle() {
    AccountingPeriod open =
        standardCalculator().periodFor(MID_MARCH, StandardPeriodGranularity.MONTHLY);

    AccountingPeriod closed = open.close();
    AccountingPeriod locked = closed.lock();
    AccountingPeriod archived = locked.archive();

    assertThat(closed.status()).isEqualTo(StandardPeriodStatus.CLOSED);
    assertThat(locked.status()).isEqualTo(StandardPeriodStatus.LOCKED);
    assertThat(archived.status()).isEqualTo(StandardPeriodStatus.ARCHIVED);
    assertThatThrownBy(locked::close).isInstanceOf(StateTransitionException.class);
  }

  @Test
  void validatorRejectsPostingIntoAClosedOrOutOfRangePeriod() {
    AccountingPeriod march =
        standardCalculator().periodFor(MID_MARCH, StandardPeriodGranularity.MONTHLY);
    PeriodValidator validator = new PeriodValidator();

    assertThat(validator.validatePosting(march, MID_MARCH).isValid()).isTrue();
    assertThat(validator.validatePosting(march.close(), MID_MARCH).isValid()).isFalse();
    OccurredAt outsidePeriod = new OccurredAt(Instant.parse("2026-04-15T00:00:00Z"));
    assertThat(validator.validatePosting(march, outsidePeriod).isValid()).isFalse();
  }

  @Test
  void validatorDetectsOverlappingPeriods() {
    PeriodCalculator calculator = standardCalculator();
    AccountingPeriod march = calculator.periodFor(MID_MARCH, StandardPeriodGranularity.MONTHLY);
    AccountingPeriod q1 = calculator.periodFor(MID_MARCH, StandardPeriodGranularity.QUARTERLY);
    PeriodValidator validator = new PeriodValidator();

    assertThat(validator.validateNoOverlaps(List.of(march, q1)).isValid()).isFalse();
    assertThat(validator.validateNoOverlaps(List.of(march, calculator.next(march))).isValid())
        .isTrue();
  }
}
