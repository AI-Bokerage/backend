package io.genfin.dunning.internal.lifecycle;

import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.ACTIVATE;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.CANCEL;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.COMPLETE;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.ESCALATE;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.FAIL;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.PAUSE;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.RESUME;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.RETRY;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.SCHEDULE;
import static io.genfin.dunning.lifecycle.StandardDunningCaseEvent.WRITE_OFF;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.ACTIVE;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.CANCELLED;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.COMPLETED;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.CREATED;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.ESCALATED;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.FAILED;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.PAUSED;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.RETRYING;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.WAITING;
import static io.genfin.dunning.lifecycle.StandardDunningCaseStatus.WRITTEN_OFF;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.StateMachines;
import io.genfin.api.statemachine.Transition;
import io.genfin.dunning.lifecycle.DunningCaseEvent;
import io.genfin.dunning.lifecycle.DunningCaseStatus;
import io.genfin.dunning.port.lifecycle.DunningCaseLifecycleProvider;
import java.util.List;

/**
 * The standard dunning case lifecycle: Created -&gt; Active, then freely between Active/Waiting/
 * Retrying/Escalated as the caller schedules and resolves reminder/retry/escalation plans, with
 * Paused/Failed/Cancelled side paths and Completed/WrittenOff as terminal outcomes. No retry
 * interval, day count, channel, or escalation action is encoded here - this is only the shape of
 * the state graph; what triggers each event is entirely up to the caller resolving a {@code
 * DunningPolicy}.
 */
public final class DefaultDunningCaseLifecycleProvider implements DunningCaseLifecycleProvider {

  @Override
  public StateMachine<DunningCaseStatus, DunningCaseEvent> create(DunningCaseStatus initialState) {
    return StateMachines.of(initialState, transitions());
  }

  private static List<Transition<DunningCaseStatus, DunningCaseEvent>> transitions() {
    return List.of(
        new Transition<>(CREATED, ACTIVATE, ACTIVE),
        new Transition<>(CREATED, CANCEL, CANCELLED),
        new Transition<>(CREATED, COMPLETE, COMPLETED),
        new Transition<>(CREATED, WRITE_OFF, WRITTEN_OFF),
        new Transition<>(ACTIVE, SCHEDULE, WAITING),
        new Transition<>(ACTIVE, RETRY, RETRYING),
        new Transition<>(ACTIVE, ESCALATE, ESCALATED),
        new Transition<>(ACTIVE, PAUSE, PAUSED),
        new Transition<>(ACTIVE, COMPLETE, COMPLETED),
        new Transition<>(ACTIVE, CANCEL, CANCELLED),
        new Transition<>(WAITING, ACTIVATE, ACTIVE),
        new Transition<>(WAITING, RETRY, RETRYING),
        new Transition<>(WAITING, ESCALATE, ESCALATED),
        new Transition<>(WAITING, PAUSE, PAUSED),
        new Transition<>(WAITING, COMPLETE, COMPLETED),
        new Transition<>(WAITING, CANCEL, CANCELLED),
        new Transition<>(RETRYING, SCHEDULE, WAITING),
        new Transition<>(RETRYING, ESCALATE, ESCALATED),
        new Transition<>(RETRYING, PAUSE, PAUSED),
        new Transition<>(RETRYING, COMPLETE, COMPLETED),
        new Transition<>(RETRYING, FAIL, FAILED),
        new Transition<>(ESCALATED, RETRY, RETRYING),
        new Transition<>(ESCALATED, PAUSE, PAUSED),
        new Transition<>(ESCALATED, COMPLETE, COMPLETED),
        new Transition<>(ESCALATED, FAIL, FAILED),
        new Transition<>(ESCALATED, WRITE_OFF, WRITTEN_OFF),
        new Transition<>(PAUSED, RESUME, ACTIVE),
        new Transition<>(PAUSED, COMPLETE, COMPLETED),
        new Transition<>(PAUSED, CANCEL, CANCELLED),
        new Transition<>(FAILED, WRITE_OFF, WRITTEN_OFF));
  }
}
