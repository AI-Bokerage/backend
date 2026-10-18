package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.calendar.TimeWindow;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.ReminderId;

/**
 * Fired when a reminder occurrence has been placed on the resolved {@code ReminderPlan}'s
 * calendar-adjusted {@link TimeWindow}. Data only - fin-dunning never sends this reminder.
 */
public record ReminderScheduled(
    EventMetadata metadata,
    DunningCaseId caseId,
    ReminderId reminderId,
    int sequenceNumber,
    TimeWindow window)
    implements DomainEvent {}
