package io.genfin.dunning.port.notification;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.notification.NotificationOccurrence;
import io.genfin.dunning.notification.NotificationPlan;
import io.genfin.dunning.notification.NotificationSchedule;
import java.util.List;

/**
 * SPI computing the full {@link NotificationPlan} across a set of {@link NotificationOccurrence}s -
 * reminder-driven, escalation-driven, or any other source an application's own planning produces -
 * by applying the given {@link NotificationSchedule}'s preferences over each occurrence's fallback
 * channel/template. Plans only - never sends a notification on any channel.
 */
@FunctionalInterface
public interface NotificationStrategy extends Extension {

  NotificationPlan plan(List<NotificationOccurrence> occurrences, NotificationSchedule schedule);
}
