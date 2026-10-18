package io.genfin.dunning.port.calendar;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.calendar.CalendarPolicy;
import java.time.LocalDate;

/**
 * Low-level business-day arithmetic against an explicit {@link CalendarPolicy}. This is the
 * mechanism; {@link BusinessCalendar} is the convenient, policy-bound facade callers should
 * normally use instead.
 */
public interface BusinessDayCalculator extends Extension {

  boolean isBusinessDay(LocalDate date, CalendarPolicy policy);

  LocalDate nextBusinessDay(LocalDate date, CalendarPolicy policy);

  LocalDate previousBusinessDay(LocalDate date, CalendarPolicy policy);

  /**
   * Steps {@code businessDays} business days forward (positive) or backward (negative) from {@code
   * date}. {@code date} itself need not be a business day; zero returns {@code date} unchanged.
   */
  LocalDate addBusinessDays(LocalDate date, int businessDays, CalendarPolicy policy);
}
