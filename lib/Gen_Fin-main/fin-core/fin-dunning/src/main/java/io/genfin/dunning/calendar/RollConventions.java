package io.genfin.dunning.calendar;

import java.time.LocalDate;

/** Factory for the standard {@link RollConvention}s, plus a hook for custom ones. */
public final class RollConventions {

  public static final RollConvention FORWARD =
      (date, isBusinessDay) -> roll(date, 1, isBusinessDay);

  public static final RollConvention BACKWARD =
      (date, isBusinessDay) -> roll(date, -1, isBusinessDay);

  public static final RollConvention UNADJUSTED = (date, isBusinessDay) -> date;

  private RollConventions() {}

  public static RollConvention custom(RollConvention convention) {
    return convention;
  }

  private static LocalDate roll(
      LocalDate date, int step, java.util.function.Predicate<LocalDate> isBusinessDay) {
    LocalDate cursor = date;
    while (!isBusinessDay.test(cursor)) {
      cursor = cursor.plusDays(step);
    }
    return cursor;
  }
}
