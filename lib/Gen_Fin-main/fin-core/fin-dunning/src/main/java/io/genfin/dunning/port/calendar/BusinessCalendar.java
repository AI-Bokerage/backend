package io.genfin.dunning.port.calendar;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.calendar.CalendarPolicy;
import java.time.LocalDate;

/**
 * The composed calendar port fin-dunning's scheduling code depends on: a {@link CalendarPolicy}
 * bound to a {@link BusinessDayCalculator}, exposing the day-level operations reminder/retry/
 * escalation scheduling actually needs. Applications resolve one of these (typically via {@code
 * io.genfin.dunning.calendar.BusinessCalendars}) from their own {@code DunningPolicy}.
 */
public interface BusinessCalendar extends Extension {

  CalendarPolicy policy();

  boolean isBusinessDay(LocalDate date);

  LocalDate nextBusinessDay(LocalDate date);

  LocalDate previousBusinessDay(LocalDate date);

  LocalDate addBusinessDays(LocalDate date, int businessDays);

  /**
   * Adjusts {@code date} onto an eligible day per this calendar's policy: unchanged when the policy
   * is not business-calendar-aware (pure calendar-day scheduling) or already a business day,
   * otherwise moved per the policy's {@code RollConvention}.
   */
  LocalDate roll(LocalDate date);
}
