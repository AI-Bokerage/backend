package io.genfin.dunning.internal.calendar;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.calendar.CalendarPolicy;
import io.genfin.dunning.port.calendar.BusinessDayCalculator;
import java.time.LocalDate;

/**
 * Walks the calendar one day at a time, deferring every "is this day eligible" decision to the
 * policy's {@code WeekendStrategy}/{@code HolidayProvider} - no weekend or holiday rule is baked in
 * here.
 */
public final class DefaultBusinessDayCalculator implements BusinessDayCalculator {

  @Override
  public boolean isBusinessDay(LocalDate date, CalendarPolicy policy) {
    Validate.notNull(date, "date must not be null.");
    Validate.notNull(policy, "policy must not be null.");
    return !policy.weekendStrategy().isWeekend(date.getDayOfWeek())
        && !policy.holidayProvider().isHoliday(date);
  }

  @Override
  public LocalDate nextBusinessDay(LocalDate date, CalendarPolicy policy) {
    Validate.notNull(date, "date must not be null.");
    return step(date, 1, policy);
  }

  @Override
  public LocalDate previousBusinessDay(LocalDate date, CalendarPolicy policy) {
    Validate.notNull(date, "date must not be null.");
    return step(date, -1, policy);
  }

  @Override
  public LocalDate addBusinessDays(LocalDate date, int businessDays, CalendarPolicy policy) {
    Validate.notNull(date, "date must not be null.");
    Validate.notNull(policy, "policy must not be null.");
    int step = businessDays >= 0 ? 1 : -1;
    int remaining = Math.abs(businessDays);
    LocalDate cursor = date;
    while (remaining > 0) {
      cursor = cursor.plusDays(step);
      if (isBusinessDay(cursor, policy)) {
        remaining--;
      }
    }
    return cursor;
  }

  private LocalDate step(LocalDate date, int direction, CalendarPolicy policy) {
    Validate.notNull(policy, "policy must not be null.");
    LocalDate cursor = date;
    do {
      cursor = cursor.plusDays(direction);
    } while (!isBusinessDay(cursor, policy));
    return cursor;
  }
}
