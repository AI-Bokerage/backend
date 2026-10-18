package io.genfin.dunning.calendar;

import io.genfin.dunning.internal.calendar.SetBasedWeekendStrategy;
import io.genfin.dunning.port.calendar.WeekendStrategy;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * Factory for {@link WeekendStrategy} instances. Saturday/Sunday is one convention among several
 * real ones (Friday/Saturday in parts of the Middle East, Friday-only elsewhere) - these are
 * example, opt-in strategies, never assumed by default deep in business logic.
 */
public final class WeekendStrategies {

  private static final WeekendStrategy SATURDAY_SUNDAY =
      new SetBasedWeekendStrategy(EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY));

  private static final WeekendStrategy FRIDAY_SATURDAY =
      new SetBasedWeekendStrategy(EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY));

  private WeekendStrategies() {}

  /** Example default: Saturday and Sunday. */
  public static WeekendStrategy satSun() {
    return SATURDAY_SUNDAY;
  }

  /** Example alternative used in several Middle Eastern markets: Friday and Saturday. */
  public static WeekendStrategy fridaySaturday() {
    return FRIDAY_SATURDAY;
  }

  /** Any other fixed set of non-working days of the week an application needs. */
  public static WeekendStrategy of(Set<DayOfWeek> weekendDays) {
    return new SetBasedWeekendStrategy(weekendDays);
  }
}
