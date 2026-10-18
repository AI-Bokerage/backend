package io.genfin.dunning.event;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.event.EventMetadata;
import io.genfin.dunning.escalation.EscalationAction;
import io.genfin.dunning.escalation.EscalationLevel;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.EscalationId;

/**
 * Fired when an {@code EscalationStrategy} decides an obligation's failed-attempt count warrants
 * escalation. Describes which action is warranted (notify manager, suspend, freeze, write off,
 * manual review, ...) - fin-dunning never performs that action itself.
 */
public record EscalationTriggered(
    EventMetadata metadata,
    DunningCaseId caseId,
    EscalationId escalationId,
    EscalationLevel level,
    EscalationAction action,
    int attemptCount)
    implements DomainEvent {}
