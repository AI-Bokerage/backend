package io.genfin.dunning.lifecycle;

/** The standard dunning case lifecycle events. */
public enum StandardDunningCaseEvent implements DunningCaseEvent {
  ACTIVATE,
  SCHEDULE,
  RETRY,
  ESCALATE,
  PAUSE,
  RESUME,
  COMPLETE,
  FAIL,
  WRITE_OFF,
  CANCEL;

  @Override
  public String code() {
    return name();
  }
}
