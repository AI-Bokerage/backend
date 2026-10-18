package io.genfin.dunning.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.backoff.BackoffStrategies;
import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.escalation.EscalationAction;
import io.genfin.dunning.escalation.EscalationDecision;
import io.genfin.dunning.escalation.EscalationLevel;
import io.genfin.dunning.escalation.StandardEscalationAction;
import io.genfin.dunning.escalation.StandardEscalationLevel;
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.policy.DunningPolicies;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.port.validation.DunningValidator;
import io.genfin.dunning.reminder.Reminder;
import io.genfin.dunning.reminder.ReminderPlan;
import io.genfin.dunning.reminder.ReminderResult;
import io.genfin.dunning.retry.RetryDecision;
import io.genfin.dunning.retry.RetryPlan;
import io.genfin.dunning.schedule.SchedulePolicies;
import io.genfin.dunning.schedule.SchedulePolicyConfiguration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Validators#standard()}'s composed default rules and the collect-everything
 * {@link ValidationResult} shape they feed.
 */
class DunningValidationTest {

  @Test
  void emptyContextProducesNoIssues() {
    DunningValidator validator = Validators.standard();

    ValidationResult result = validator.validate(ValidationContext.empty());

    assertThat(result.isValid()).isTrue();
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void standardDunningPolicyIsValid() {
    DunningValidator validator = Validators.standard();
    DunningPolicy policy = DunningPolicies.standard();

    ValidationResult result = validator.validate(ValidationContext.of(policy));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void policyWithNegativeMaxRetriesIsFlagged() {
    DunningValidator validator = Validators.standard();
    RetryPolicy negativeRetries =
        new RetryPolicy() {
          @Override
          public io.genfin.dunning.port.backoff.BackoffStrategy backoffStrategy() {
            return BackoffStrategies.fixed(BackoffInterval.of(1, ChronoUnit.DAYS));
          }

          @Override
          public io.genfin.dunning.port.calendar.BusinessCalendar businessCalendar() {
            return io.genfin.dunning.calendar.BusinessCalendars.standard();
          }

          @Override
          public java.time.ZoneId zone() {
            return java.time.ZoneOffset.UTC;
          }

          @Override
          public int maxRetries() {
            return -1;
          }
        };
    DunningPolicy policy =
        DunningPolicies.builder()
            .schedulePolicy(
                SchedulePolicies.of(
                    SchedulePolicyConfiguration.builder().retryPolicy(negativeRetries).build()))
            .build();

    ValidationResult result = validator.validate(ValidationContext.of(policy));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> "DUNNING_POLICY".equals(issue.ruleCode()));
  }

  @Test
  void retryPlanWithNonSequentialAttemptNumbersIsFlagged() {
    DunningValidator validator = Validators.standard();
    RetryDecision decision =
        RetryDecision.scheduled(
            2,
            io.genfin.dunning.calendar.RetryWindow.of(
                2,
                io.genfin.dunning.calendar.TimeWindow.of(
                    Instant.EPOCH, Instant.EPOCH.plus(1, ChronoUnit.DAYS))));
    RetryPlan plan = RetryPlan.of(List.of(decision));

    ValidationResult result = validator.validate(ValidationContext.empty().withRetryPlan(plan));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> "RETRY_PLAN".equals(issue.ruleCode()));
  }

  @Test
  void reminderPlanWithDuplicateSequenceNumbersIsFlagged() {
    DunningValidator validator = Validators.standard();
    Reminder reminder =
        new Reminder(
            1,
            io.genfin.dunning.reminder.StandardReminderChannel.EMAIL,
            io.genfin.dunning.reminder.ReminderTemplateReference.of("t"),
            io.genfin.dunning.calendar.TimeWindow.of(
                Instant.EPOCH, Instant.EPOCH.plus(1, ChronoUnit.DAYS)));
    ReminderPlan plan =
        ReminderPlan.of(
            List.of(ReminderResult.planned(reminder), ReminderResult.planned(reminder)));

    ValidationResult result = validator.validate(ValidationContext.empty().withReminderPlan(plan));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> "REMINDER_PLAN".equals(issue.ruleCode()));
  }

  @Test
  void escalationDecisionEscalatingAtZeroAttemptsIsFlagged() {
    DunningValidator validator = Validators.standard();
    EscalationLevel level = StandardEscalationLevel.LEVEL_1;
    EscalationAction action = StandardEscalationAction.NOTIFY_MANAGER;
    EscalationDecision decision = EscalationDecision.escalate(0, level, action);

    ValidationResult result =
        validator.validate(ValidationContext.empty().withEscalationDecision(decision));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> "ESCALATION_DECISION".equals(issue.ruleCode()));
  }

  @Test
  void collectionPlanWithDuplicateStagesIsFlagged() {
    DunningValidator validator = Validators.standard();
    CollectionPlan plan =
        new CollectionPlan(
            io.genfin.dunning.id.CollectionPlanId.generate(),
            DunningPolicyId.generate(),
            List.of(CollectionStage.REMINDING, CollectionStage.REMINDING));

    ValidationResult result =
        validator.validate(ValidationContext.empty().withCollectionPlan(plan));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> "COLLECTION_PLAN".equals(issue.ruleCode()));
  }

  @Test
  void validationResultValidWhenEmpty() {
    assertThat(ValidationResult.valid().isValid()).isTrue();
    assertThat(ValidationResult.valid().issues()).isEmpty();
  }
}
