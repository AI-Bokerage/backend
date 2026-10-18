package io.genfin.dunning.internal.calendar;

import io.genfin.dunning.port.calendar.WeekendStrategy;
import java.time.DayOfWeek;
import java.util.Set;

/** Treats a fixed, caller-supplied set of days of the week as the weekend. */
public final class SetBasedWeekendStrategy implements WeekendStrategy {

  private final Set<DayOfWeek> weekendDays;

  public SetBasedWeekendStrategy(Set<DayOfWeek> weekendDays) {
    this.weekendDays = Set.copyOf(weekendDays);
  }

  @Override
  public boolean isWeekend(DayOfWeek dayOfWeek) {
    return weekendDays.contains(dayOfWeek);
  }
}
