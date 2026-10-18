package io.genfin.dunning.port.calendar;

import io.genfin.api.port.spi.Extension;
import java.time.DayOfWeek;

/**
 * SPI deciding which {@link DayOfWeek}s count as a non-working weekend for a deployment. Sat/Sun is
 * only one convention among many real ones (e.g. Friday/Saturday in several Middle Eastern markets)
 * - fin-dunning never assumes it, applications supply the strategy that fits their business.
 */
public interface WeekendStrategy extends Extension {

  boolean isWeekend(DayOfWeek dayOfWeek);
}
