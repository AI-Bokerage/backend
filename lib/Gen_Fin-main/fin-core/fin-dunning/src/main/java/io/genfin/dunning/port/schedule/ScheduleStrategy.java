package io.genfin.dunning.port.schedule;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.schedule.DunningSchedule;

/**
 * SPI computing the full {@link DunningSchedule} for a {@link FinancialObligation}: composes the
 * resolved {@link SchedulePolicy}'s grace period, reminder offsets, and retry policy onto its
 * {@code BusinessCalendar}. Plans only - never sends a reminder or executes a retry.
 */
@FunctionalInterface
public interface ScheduleStrategy extends Extension {

  DunningSchedule schedule(FinancialObligation obligation, SchedulePolicy policy);
}
