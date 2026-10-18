package io.genfin.dunning.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.port.calendar.BusinessCalendar;
import io.genfin.dunning.port.calendar.HolidayProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class BusinessCalendarTest {

  private static final HolidayProvider MONDAY_HOLIDAY =
      date -> date.equals(LocalDate.of(2026, 8, 3));

  @Test
  void satSunAreWeekendsByDefaultAndFridaySaturdayIsAnOptionalAlternative() {
    assertThat(WeekendStrategies.satSun().isWeekend(java.time.DayOfWeek.SATURDAY)).isTrue();
    assertThat(WeekendStrategies.satSun().isWeekend(java.time.DayOfWeek.FRIDAY)).isFalse();
    assertThat(WeekendStrategies.fridaySaturday().isWeekend(java.time.DayOfWeek.FRIDAY)).isTrue();
  }

  @Test
  void nextBusinessDaySkipsWeekendsAndHolidays() {
    // Sat 2026-08-01, Sun 2026-08-02, holiday Mon 2026-08-03 -> next business day is Tue 08-04.
    BusinessCalendar calendar =
        BusinessCalendars.of(CalendarPolicy.builder().holidayProvider(MONDAY_HOLIDAY).build());

    assertThat(calendar.nextBusinessDay(LocalDate.of(2026, 7, 31)))
        .isEqualTo(LocalDate.of(2026, 8, 4));
  }

  @Test
  void rollIsANoOpWhenCalendarIsNotBusinessCalendarAware() {
    BusinessCalendar pureCalendarDay =
        BusinessCalendars.of(CalendarPolicy.builder().businessCalendarAware(false).build());
    LocalDate saturday = LocalDate.of(2026, 8, 1);

    assertThat(pureCalendarDay.roll(saturday)).isEqualTo(saturday);
  }

  @Test
  void rollForwardMovesOntoTheNextBusinessDay() {
    BusinessCalendar calendar = BusinessCalendars.standard();
    LocalDate saturday = LocalDate.of(2026, 8, 1);

    assertThat(calendar.roll(saturday)).isEqualTo(LocalDate.of(2026, 8, 3));
  }

  @Test
  void rollBackwardConventionMovesOntoThePreviousBusinessDay() {
    BusinessCalendar calendar =
        BusinessCalendars.of(
            CalendarPolicy.builder().rollConvention(RollConventions.BACKWARD).build());
    LocalDate saturday = LocalDate.of(2026, 8, 1);

    assertThat(calendar.roll(saturday)).isEqualTo(LocalDate.of(2026, 7, 31));
  }

  @Test
  void addBusinessDaysSkipsNonBusinessDays() {
    BusinessCalendar calendar = BusinessCalendars.standard();
    // Fri 2026-07-31 + 1 business day -> Mon 2026-08-03 (skips Sat/Sun).
    assertThat(calendar.addBusinessDays(LocalDate.of(2026, 7, 31), 1))
        .isEqualTo(LocalDate.of(2026, 8, 3));
  }

  @Test
  void gracePeriodExpiresAfterTheConfiguredArbitraryUnit() {
    GracePeriod threeHours = GracePeriod.of(3, ChronoUnit.HOURS);
    Instant dueDate = Instant.parse("2026-08-01T00:00:00Z");

    assertThat(threeHours.hasExpired(dueDate, dueDate.plus(2, ChronoUnit.HOURS))).isFalse();
    assertThat(threeHours.hasExpired(dueDate, dueDate.plus(4, ChronoUnit.HOURS))).isTrue();
  }

  @Test
  void retryWindowAdjustsACandidateInstantOntoTheRolledCalendarDay() {
    BusinessCalendar calendar = BusinessCalendars.standard();
    Instant candidate = LocalDate.of(2026, 8, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

    RetryWindow window = RetryWindow.adjusted(1, candidate, ZoneOffset.UTC, calendar);

    assertThat(window.attemptNumber()).isEqualTo(1);
    assertThat(window.earliest())
        .isEqualTo(LocalDate.of(2026, 8, 3).atStartOfDay(ZoneOffset.UTC).toInstant());
    assertThat(window.permits(window.earliest())).isTrue();
    assertThat(window.permits(window.latest())).isFalse();
  }
}
