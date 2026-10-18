package io.genfin.dunning.calendar;

import io.genfin.dunning.internal.calendar.DefaultBusinessCalendar;
import io.genfin.dunning.internal.calendar.DefaultBusinessDayCalculator;
import io.genfin.dunning.port.calendar.BusinessCalendar;
import io.genfin.dunning.port.calendar.BusinessDayCalculator;

/** Factory for {@link BusinessCalendar} instances. */
public final class BusinessCalendars {

  private static final BusinessDayCalculator CALCULATOR = new DefaultBusinessDayCalculator();

  private BusinessCalendars() {}

  /** A calendar bound to the given policy, using the standard day-stepping calculator. */
  public static BusinessCalendar of(CalendarPolicy policy) {
    return new DefaultBusinessCalendar(CALCULATOR, policy);
  }

  /** A calendar bound to a fully-defaulted example policy - see {@link CalendarPolicy.Builder}. */
  public static BusinessCalendar standard() {
    return of(CalendarPolicy.builder().build());
  }
}
