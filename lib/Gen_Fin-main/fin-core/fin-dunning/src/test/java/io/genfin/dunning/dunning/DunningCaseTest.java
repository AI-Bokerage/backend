package io.genfin.dunning.dunning;

import static io.genfin.dunning.support.TestCurrencies.USD;
import static io.genfin.dunning.support.TestObligationTypes.INVOICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.time.ClockProviders;
import io.genfin.dunning.calendar.RetryWindow;
import io.genfin.dunning.calendar.TimeWindow;
import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.escalation.EscalationDecision;
import io.genfin.dunning.escalation.StandardEscalationAction;
import io.genfin.dunning.escalation.StandardEscalationLevel;
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
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.id.EscalationId;
import io.genfin.dunning.id.ReminderId;
import io.genfin.dunning.id.RetryId;
import io.genfin.dunning.lifecycle.StandardDunningCaseStatus;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.reference.Reference;
import io.genfin.dunning.reminder.Reminder;
import io.genfin.dunning.reminder.ReminderTemplateReference;
import io.genfin.dunning.reminder.StandardReminderChannel;
import io.genfin.dunning.retry.RetryDecision;
import io.genfin.dunning.retry.RetryExecution;
import io.genfin.dunning.retry.RetryResult;
import io.genfin.money.money.Money;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DunningCaseTest {

  private static final ClockProvider CLOCK =
      ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z"));

  private static DunningCase newCase() {
    FinancialObligation obligation =
        FinancialObligation.of(
            Money.of("100.00", USD),
            Instant.parse("2025-12-01T00:00:00Z"),
            INVOICE,
            Reference.obligationSource("inv-1"));
    CollectionPlan plan = CollectionPlan.standard(DunningPolicyId.generate());
    return new DunningCase(DunningCaseId.generate(), obligation, plan, CLOCK);
  }

  @Test
  void opensInTheOpenStageAndRecordsAnOpenedEvent() {
    DunningCase dunningCase = newCase();

    assertThat(dunningCase.stage()).isEqualTo(CollectionStage.OPEN);
    assertThat(dunningCase.status()).isEqualTo(StandardDunningCaseStatus.CREATED);
    assertThat(dunningCase.pullEvents()).hasSize(1).first().isInstanceOf(DunningCaseOpened.class);
  }

  @Test
  void lifecycleMovesThroughActiveWaitingRetryingAsTheCaseIsWorked() {
    DunningCase dunningCase = newCase();
    dunningCase.pullEvents();

    dunningCase.activate(CLOCK);
    assertThat(dunningCase.status()).isEqualTo(StandardDunningCaseStatus.ACTIVE);
    assertThat(dunningCase.pullEvents()).hasSize(1).first().isInstanceOf(DunningStarted.class);

    RetryId retryId = RetryId.generate();
    RetryDecision decision =
        RetryDecision.scheduled(
            1, RetryWindow.of(1, TimeWindow.of(CLOCK.now(), CLOCK.now().plusSeconds(3600))));
    dunningCase.retry(retryId, decision, CLOCK);
    assertThat(dunningCase.status()).isEqualTo(StandardDunningCaseStatus.RETRYING);
    assertThat(dunningCase.pullEvents()).hasSize(1).first().isInstanceOf(RetryPlanned.class);

    dunningCase.recordRetryExecution(
        new RetryExecution(
            RetryId.generate(), 1, CLOCK.now(), CLOCK.now(), RetryResult.FAILED, null),
        CLOCK);
    assertThat(dunningCase.pullEvents()).hasSize(1).first().isInstanceOf(RetryExecuted.class);

    EscalationDecision escalationDecision =
        EscalationDecision.escalate(
            1, StandardEscalationLevel.LEVEL_1, StandardEscalationAction.NOTIFY_MANAGER);
    dunningCase.escalate(EscalationId.generate(), escalationDecision, CLOCK);
    assertThat(dunningCase.status()).isEqualTo(StandardDunningCaseStatus.ESCALATED);
    assertThat(dunningCase.pullEvents()).hasSize(1).first().isInstanceOf(EscalationTriggered.class);

    assertThatThrownBy(dunningCase::resume).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void planReminderEmitsScheduledAndGeneratedEvents() {
    DunningCase dunningCase = newCase();
    dunningCase.pullEvents();

    Reminder reminder =
        new Reminder(
            1,
            StandardReminderChannel.EMAIL,
            ReminderTemplateReference.of("overdue-1"),
            TimeWindow.of(CLOCK.now(), CLOCK.now().plusSeconds(3600)));
    dunningCase.planReminder(ReminderId.generate(), reminder, CLOCK);

    assertThat(dunningCase.pullEvents())
        .hasSize(2)
        .anyMatch(ReminderScheduled.class::isInstance)
        .anyMatch(ReminderGenerated.class::isInstance);
  }

  @Test
  void completeAndWriteOffAlsoDriveTheLifecycleStatus() {
    DunningCase completed = newCase();
    completed.pullEvents();
    completed.complete("paid in full", CLOCK);
    assertThat(completed.status()).isEqualTo(StandardDunningCaseStatus.COMPLETED);
    assertThat(completed.pullEvents()).anyMatch(CollectionCompleted.class::isInstance);

    DunningCase writtenOff = newCase();
    writtenOff.pullEvents();
    writtenOff.writeOff("uncollectable", CLOCK);
    assertThat(writtenOff.status()).isEqualTo(StandardDunningCaseStatus.WRITTEN_OFF);
    assertThat(writtenOff.pullEvents()).anyMatch(CollectionWrittenOff.class::isInstance);
  }

  @Test
  void failEmitsACollectionFailedEvent() {
    DunningCase dunningCase = newCase();
    dunningCase.activate(CLOCK);
    RetryDecision decision =
        RetryDecision.scheduled(
            1, RetryWindow.of(1, TimeWindow.of(CLOCK.now(), CLOCK.now().plusSeconds(3600))));
    dunningCase.retry(RetryId.generate(), decision, CLOCK);
    dunningCase.pullEvents();

    dunningCase.fail("all retries exhausted", CLOCK);

    assertThat(dunningCase.status()).isEqualTo(StandardDunningCaseStatus.FAILED);
    assertThat(dunningCase.pullEvents()).hasSize(1).first().isInstanceOf(CollectionFailed.class);
  }

  @Test
  void advancingThroughThePlanAppendsHistoryAndEmitsAStageAdvancedEvent() {
    DunningCase dunningCase = newCase();
    dunningCase.pullEvents();

    dunningCase.advanceTo(CollectionStage.REMINDING, "first reminder due", CLOCK);

    assertThat(dunningCase.stage()).isEqualTo(CollectionStage.REMINDING);
    assertThat(dunningCase.history().entries()).hasSize(1);
    assertThat(dunningCase.pullEvents())
        .hasSize(1)
        .first()
        .isInstanceOf(DunningCaseStageAdvanced.class);
  }

  @Test
  void rejectsAdvancingToAStageNotPartOfThePlan() {
    DunningCase dunningCase = newCase();

    assertThatThrownBy(() -> dunningCase.advanceTo(CollectionStage.OPEN, "n/a", CLOCK))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void writeOffClosesTheCaseAndEmitsAClosedEvent() {
    DunningCase dunningCase = newCase();
    dunningCase.pullEvents();

    dunningCase.writeOff("uncollectable", CLOCK);

    assertThat(dunningCase.isClosed()).isTrue();
    assertThat(dunningCase.pullEvents()).hasSize(3).anyMatch(DunningCaseClosed.class::isInstance);
  }

  @Test
  void rejectsAdvancingAClosedCase() {
    DunningCase dunningCase = newCase();
    dunningCase.complete("paid in full", CLOCK);

    assertThatThrownBy(() -> dunningCase.advanceTo(CollectionStage.RETRYING, "n/a", CLOCK))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void summaryCountsHistoryEntriesPerStage() {
    DunningCase dunningCase = newCase();
    dunningCase.advanceTo(CollectionStage.REMINDING, "reminder 1", CLOCK);
    dunningCase.advanceTo(CollectionStage.RETRYING, "retry 1", CLOCK);

    var summary = dunningCase.summary();

    assertThat(summary.reminderCount()).isEqualTo(1);
    assertThat(summary.retryCount()).isEqualTo(1);
    assertThat(summary.escalationCount()).isEqualTo(0);
    assertThat(summary.currentStage()).isEqualTo(CollectionStage.RETRYING);
  }
}
