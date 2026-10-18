package io.genfin.dunning.port.calendar;

import io.genfin.api.port.spi.Extension;
import java.time.LocalDate;

/**
 * SPI for an application's own regional/business holiday calendar. fin-dunning ships no real
 * holiday data of any kind - a consuming application implements this against whatever regional,
 * national, or org-specific calendar it needs (and may register a different instance per region).
 */
public interface HolidayProvider extends Extension {

  boolean isHoliday(LocalDate date);
}
