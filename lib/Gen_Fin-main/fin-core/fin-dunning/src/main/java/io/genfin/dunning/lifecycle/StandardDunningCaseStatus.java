package io.genfin.dunning.lifecycle;

/**
 * The standard dunning case lifecycle states. This is the operational status of the collection
 * effort itself (is it actively working, waiting on a scheduled action, retrying, escalated,
 * paused, or closed) - it is orthogonal to {@link io.genfin.dunning.collection.CollectionStage},
 * which tracks which phase of the resolved {@code CollectionPlan} the case has reached. Neither
 * axis hardcodes any interval, channel, or escalation action; both are driven entirely by the
 * caller resolving policy/plans and firing the corresponding lifecycle events.
 */
public enum StandardDunningCaseStatus implements DunningCaseStatus {
  CREATED,
  ACTIVE,
  WAITING,
  RETRYING,
  ESCALATED,
  PAUSED,
  COMPLETED,
  FAILED,
  WRITTEN_OFF,
  CANCELLED;

  @Override
  public String code() {
    return name();
  }
}
