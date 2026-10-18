package io.genfin.dunning.internal.calendar;

import io.genfin.dunning.port.calendar.HolidayProvider;
import java.time.LocalDate;

/** Trivial "no holidays" default - fin-dunning ships no real holiday data. */
public final class NoHolidayProvider implements HolidayProvider {

  @Override
  public boolean isHoliday(LocalDate date) {
    return false;
  }
}
