package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.ReminderId;
import io.genfin.dunning.reminder.ReminderChannel;
import io.genfin.dunning.reminder.ReminderTemplateReference;

/**
 * Fired when a scheduled reminder occurrence has its channel/template content resolved by a {@code
 * ReminderRule}, i.e. it is now a concrete, dispatch-ready {@code Reminder}. Data only -
 * fin-dunning never dispatches it on the resolved channel.
 */
public record ReminderGenerated(
    EventMetadata metadata,
    DunningCaseId caseId,
    ReminderId reminderId,
    int sequenceNumber,
    ReminderChannel channel,
    ReminderTemplateReference template)
    implements DomainEvent {}
