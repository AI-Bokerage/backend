package io.genfin.dunning.calendar;

import io.genfin.dunning.internal.calendar.NoHolidayProvider;
import io.genfin.dunning.port.calendar.HolidayProvider;

/**
 * Factory for {@link HolidayProvider} instances. fin-dunning ships no real holiday data - {@link
 * #none()} is the only built-in, purely so example/default configurations compile; a real
 * deployment always supplies its own regional/business holiday calendar.
 */
public final class HolidayProviders {

  private static final HolidayProvider NONE = new NoHolidayProvider();

  private HolidayProviders() {}

  public static HolidayProvider none() {
    return NONE;
  }
}
