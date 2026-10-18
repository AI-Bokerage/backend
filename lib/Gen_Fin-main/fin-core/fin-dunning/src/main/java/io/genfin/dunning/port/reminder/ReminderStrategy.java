package io.genfin.dunning.port.reminder;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.reminder.ReminderContext;
import io.genfin.dunning.reminder.ReminderPlan;
import io.genfin.dunning.schedule.DunningSchedule;

/**
 * SPI computing the full {@link ReminderPlan} for an obligation: resolves each {@code REMINDER}
 * entry of its {@link DunningSchedule} against the resolved {@link ReminderPolicy}'s {@code
 * ReminderSchedule} rules, honoring already-dispatched occurrences in the given {@link
 * ReminderContext}. Plans only - never sends a reminder on any channel.
 */
@FunctionalInterface
public interface ReminderStrategy extends Extension {

  ReminderPlan plan(
      FinancialObligation obligation,
      DunningSchedule schedule,
      ReminderPolicy policy,
      ReminderContext context);
}
