package io.genfin.dunning.collection;

/**
 * The structural phase a {@code DunningCase} is currently in within the fixed pipeline this module
 * implements: {@code FinancialObligation -> Policy Resolution -> Reminder Plan -> Retry Plan ->
 * Escalation Plan -> Completion/Write-off}. This is the shape of the pipeline itself, not a
 * business policy - the same way {@code AccountClassification} is structural to double-entry
 * bookkeeping. How many reminders/retries happen, on what cadence, and which escalation actions
 * fire are never decided here - they come from the {@code DunningPolicy} resolved for the case.
 */
public enum CollectionStage {

  /** Case opened; policy not yet resolved or no reminder due yet. */
  OPEN,

  /** Reminder(s) from the resolved {@code ReminderPlan} are outstanding or in flight. */
  REMINDING,

  /** Retry attempts from the resolved {@code RetryPlan} are outstanding or in flight. */
  RETRYING,

  /** An {@code EscalationDecision} has been produced and is pending the application's action. */
  ESCALATING,

  /** The obligation was satisfied; the case is closed with no further action. */
  COMPLETED,

  /** The obligation was written off; the case is closed as uncollectable. */
  WRITTEN_OFF
}
