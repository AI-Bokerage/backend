package io.genfin.dunning.dunning;

import io.genfin.api.domain.AggregateRoot;
import io.genfin.api.event.EventMetadata;
import io.genfin.api.event.OccurredAt;
import io.genfin.api.id.AggregateId;
import io.genfin.api.id.CorrelationId;
import io.genfin.api.id.EventId;
import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.statemachine.TransitionResult;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.collection.CollectionAttributes;
import io.genfin.dunning.collection.CollectionHistory;
import io.genfin.dunning.collection.CollectionHistoryEntry;
import io.genfin.dunning.collection.CollectionMetadata;
import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.collection.CollectionSummary;
import io.genfin.dunning.escalation.EscalationDecision;
import io.genfin.dunning.event.CollectionCompleted;
import io.genfin.dunning.event.CollectionFailed;
import io.genfin.dunning.event.CollectionWrittenOff;
import io.genfin.dunning.event.DunningCaseClosed;
import io.genfin.dunning.event.DunningCaseOpened;
import io.genfin.dunning.event.DunningCaseStageAdvanced;
import io.genfin.dunning.event.DunningStarted;
import io.genfin.dunning.event.EscalationTriggered;
import io.genfin.dunning.event.ReminderGenerated;
import io.genfin.dunning.event.ReminderScheduled;
import io.genfin.dunning.event.RetryExecuted;
import io.genfin.dunning.event.RetryPlanned;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.id.EscalationId;
import io.genfin.dunning.id.ReminderId;
import io.genfin.dunning.id.RetryId;
import io.genfin.dunning.lifecycle.DunningCaseEvent;
import io.genfin.dunning.lifecycle.DunningCaseLifecycles;
import io.genfin.dunning.lifecycle.DunningCaseStatus;
import io.genfin.dunning.lifecycle.StandardDunningCaseEvent;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.reminder.Reminder;
import io.genfin.dunning.retry.RetryDecision;
import io.genfin.dunning.retry.RetryExecution;

/**
 * The dunning aggregate root: a single collection effort against one {@link FinancialObligation},
 * progressing through the {@link CollectionStage}s of its {@link CollectionPlan}. This engine only
 * ever produces plans/decisions and records what stage a case has reached - it never sends a
 * reminder, retries a payment, or performs an escalation action itself.
 *
 * <p>{@link #stage()} tracks which phase of the resolved {@link CollectionPlan} the case has
 * reached; {@link #status()} tracks the case's operational lifecycle (active/waiting/retrying/
 * escalated/paused/closed) and is driven entirely by the SPI-replaceable {@link
 * io.genfin.dunning.port.lifecycle.DunningCaseLifecycleProvider} - this aggregate holds no
 * transition table of its own, mirroring {@code io.genfin.ledger.journal.JournalEntry}.
 */
public final class DunningCase extends AggregateRoot<DunningCaseId> {

  private final FinancialObligation obligation;
  private final CollectionPlan plan;
  private final StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle;

  private CollectionStage stage;
  private CollectionHistory history;
  private CollectionMetadata metadata;
  private CollectionAttributes attributes;

  public DunningCase(
      DunningCaseId id,
      FinancialObligation obligation,
      CollectionPlan plan,
      ClockProvider clockProvider) {
    this(id, obligation, plan, clockProvider, DunningCaseLifecycles.created());
  }

  public DunningCase(
      DunningCaseId id,
      FinancialObligation obligation,
      CollectionPlan plan,
      ClockProvider clockProvider,
      StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle) {
    super(id);
    this.obligation = Validate.notNull(obligation, "obligation must not be null.");
    this.plan = Validate.notNull(plan, "plan must not be null.");
    Validate.notNull(clockProvider, "clockProvider must not be null.");
    this.lifecycle = Validate.notNull(lifecycle, "lifecycle must not be null.");
    this.stage = CollectionStage.OPEN;
    this.history = CollectionHistory.empty();
    this.metadata = CollectionMetadata.empty();
    this.attributes = CollectionAttributes.standard();
    registerEvent(new DunningCaseOpened(newMetadata(clockProvider), id(), obligation.id()));
  }

  public FinancialObligation obligation() {
    return obligation;
  }

  public CollectionPlan plan() {
    return plan;
  }

  public CollectionStage stage() {
    return stage;
  }

  public DunningCaseStatus status() {
    return lifecycle.currentState();
  }

  public CollectionHistory history() {
    return history;
  }

  public CollectionMetadata metadata() {
    return metadata;
  }

  public CollectionAttributes attributes() {
    return attributes;
  }

  /**
   * Advances the case to {@code nextStage}. The stage itself must be part of this case's resolved
   * {@link CollectionPlan}, or be one of the terminal stages ({@link CollectionStage#COMPLETED},
   * {@link CollectionStage#WRITTEN_OFF}) - which stage runs when, and what plan (reminder/retry/
   * escalation) drives it, is entirely up to the caller resolving those plans from policy.
   */
  public void advanceTo(CollectionStage nextStage, String note, ClockProvider clockProvider) {
    Validate.notNull(nextStage, "nextStage must not be null.");
    Validate.notNull(clockProvider, "clockProvider must not be null.");
    Validate.state(!isClosed(), "Cannot advance a closed dunning case.");
    Validate.argument(
        plan.includes(nextStage)
            || nextStage == CollectionStage.COMPLETED
            || nextStage == CollectionStage.WRITTEN_OFF,
        "nextStage must be part of this case's collection plan or a terminal stage.");
    CollectionStage previousStage = stage;
    stage = nextStage;
    history = history.append(new CollectionHistoryEntry(nextStage, clockProvider.now(), note));
    registerEvent(
        new DunningCaseStageAdvanced(newMetadata(clockProvider), id(), previousStage, nextStage));
    if (isClosed()) {
      registerEvent(new DunningCaseClosed(newMetadata(clockProvider), id(), stage, note));
    }
  }

  /** Convenience for {@code advanceTo(COMPLETED, ...)} - the obligation was satisfied. */
  public void complete(String note, ClockProvider clockProvider) {
    advanceTo(CollectionStage.COMPLETED, note, clockProvider);
    registerEvent(new CollectionCompleted(newMetadata(clockProvider), id(), obligation.id(), note));
    fireLifecycle(StandardDunningCaseEvent.COMPLETE);
  }

  /** Convenience for {@code advanceTo(WRITTEN_OFF, ...)} - the obligation is uncollectable. */
  public void writeOff(String reason, ClockProvider clockProvider) {
    advanceTo(CollectionStage.WRITTEN_OFF, reason, clockProvider);
    registerEvent(new CollectionWrittenOff(newMetadata(clockProvider), id(), reason));
    fireLifecycle(StandardDunningCaseEvent.WRITE_OFF);
  }

  /** The case is now actively being worked (a reminder/retry/escalation is in flight). */
  public void activate(ClockProvider clockProvider) {
    registerEvent(new DunningStarted(newMetadata(clockProvider), id(), obligation.id()));
    fireLifecycle(StandardDunningCaseEvent.ACTIVATE);
  }

  /** The case is waiting on the next scheduled reminder/retry from its resolved plan. */
  public void schedule() {
    fireLifecycle(StandardDunningCaseEvent.SCHEDULE);
  }

  /**
   * Places one resolved, dispatch-ready {@code Reminder} onto the case's schedule - recording both
   * that its occurrence was scheduled and that its channel/template content was generated.
   * Fin-dunning never dispatches it.
   */
  public void planReminder(ReminderId reminderId, Reminder reminder, ClockProvider clockProvider) {
    Validate.notNull(reminderId, "reminderId must not be null.");
    Validate.notNull(reminder, "reminder must not be null.");
    registerEvent(
        new ReminderScheduled(
            newMetadata(clockProvider),
            id(),
            reminderId,
            reminder.sequenceNumber(),
            reminder.window()));
    registerEvent(
        new ReminderGenerated(
            newMetadata(clockProvider),
            id(),
            reminderId,
            reminder.sequenceNumber(),
            reminder.channel(),
            reminder.template()));
  }

  /**
   * A not-yet-exhausted attempt from the resolved {@code RetryPlan} is now outstanding or in
   * flight. Fin-dunning never executes it.
   */
  public void retry(RetryId retryId, RetryDecision decision, ClockProvider clockProvider) {
    Validate.notNull(retryId, "retryId must not be null.");
    Validate.notNull(decision, "decision must not be null.");
    Validate.argument(!decision.exhausted(), "decision must not be exhausted.");
    registerEvent(
        new RetryPlanned(
            newMetadata(clockProvider),
            id(),
            retryId,
            decision.attemptNumber(),
            decision.scheduledAt().orElseThrow()));
    fireLifecycle(StandardDunningCaseEvent.RETRY);
  }

  /** Records the outcome of an attempt the consuming application actually performed. */
  public void recordRetryExecution(RetryExecution execution, ClockProvider clockProvider) {
    Validate.notNull(execution, "execution must not be null.");
    registerEvent(
        new RetryExecuted(
            newMetadata(clockProvider),
            id(),
            execution.id(),
            execution.attemptNumber(),
            execution.result()));
  }

  /**
   * An {@code EscalationDecision} has been produced and is pending the application's action - which
   * action to take (notify manager, suspend, freeze, write off, manual review, ...) is described by
   * {@code decision}; fin-dunning never performs it.
   */
  public void escalate(
      EscalationId escalationId, EscalationDecision decision, ClockProvider clockProvider) {
    Validate.notNull(escalationId, "escalationId must not be null.");
    Validate.notNull(decision, "decision must not be null.");
    Validate.argument(decision.escalated(), "decision must be escalated.");
    registerEvent(
        new EscalationTriggered(
            newMetadata(clockProvider),
            id(),
            escalationId,
            decision.level(),
            decision.action(),
            decision.attemptCount()));
    fireLifecycle(StandardDunningCaseEvent.ESCALATE);
  }

  /** The application has paused this case (e.g. a dispute is open); no action is due. */
  public void pause() {
    fireLifecycle(StandardDunningCaseEvent.PAUSE);
  }

  /** Resumes a paused case back to {@code ACTIVE}. */
  public void resume() {
    fireLifecycle(StandardDunningCaseEvent.RESUME);
  }

  /** The collection effort itself failed (e.g. all retries exhausted with no resolution). */
  public void fail(String reason, ClockProvider clockProvider) {
    registerEvent(new CollectionFailed(newMetadata(clockProvider), id(), reason));
    fireLifecycle(StandardDunningCaseEvent.FAIL);
  }

  /** The application cancelled this case before any outcome was reached. */
  public void cancel() {
    fireLifecycle(StandardDunningCaseEvent.CANCEL);
  }

  private void fireLifecycle(DunningCaseEvent event) {
    TransitionResult<DunningCaseStatus> result = lifecycle.fire(event);
    if (!result.isAllowed()) {
      throw new IllegalStateException(
          "Cannot apply "
              + event.code()
              + " while dunning case is "
              + lifecycle.currentState().code()
              + ".");
    }
  }

  public boolean isClosed() {
    return stage == CollectionStage.COMPLETED || stage == CollectionStage.WRITTEN_OFF;
  }

  public void putMetadata(String key, String value) {
    metadata = metadata.with(Validate.notBlank(key, "key must not be blank."), value);
  }

  public void updateAttributes(CollectionAttributes attributes) {
    this.attributes = Validate.notNull(attributes, "attributes must not be null.");
  }

  public CollectionSummary summary() {
    return CollectionSummary.of(stage, history);
  }

  private EventMetadata newMetadata(ClockProvider clockProvider) {
    return new EventMetadata(
        EventId.generate(),
        OccurredAt.now(clockProvider),
        CorrelationId.generate(),
        AggregateId.of(id().value()));
  }
}
