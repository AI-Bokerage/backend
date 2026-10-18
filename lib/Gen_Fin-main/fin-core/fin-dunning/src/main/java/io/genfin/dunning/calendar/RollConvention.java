package io.genfin.dunning.calendar;

import java.time.LocalDate;
import java.util.function.Predicate;

/**
 * How to adjust a date that lands on a non-business day onto a business day. Deliberately a
 * strategy, not a fixed rule - "roll forward", "roll backward", and "leave unadjusted" (pure
 * calendar-day scheduling) are all legitimate conventions a {@code DunningPolicy} may pick, and an
 * application may supply any other custom convention (e.g. modified-following).
 */
@FunctionalInterface
public interface RollConvention {

  LocalDate roll(LocalDate date, Predicate<LocalDate> isBusinessDay);
}
