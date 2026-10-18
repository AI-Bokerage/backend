package io.genfin.dunning.lifecycle;

import io.genfin.api.statemachine.StateMachine;
import io.genfin.dunning.internal.lifecycle.DefaultDunningCaseLifecycleProvider;
import io.genfin.dunning.port.lifecycle.DunningCaseLifecycleProvider;

/** Access point for the standard {@link DunningCaseLifecycleProvider}. */
public final class DunningCaseLifecycles {

  private static final DunningCaseLifecycleProvider STANDARD =
      new DefaultDunningCaseLifecycleProvider();

  private DunningCaseLifecycles() {}

  public static DunningCaseLifecycleProvider standard() {
    return STANDARD;
  }

  public static StateMachine<DunningCaseStatus, DunningCaseEvent> created() {
    return STANDARD.create(StandardDunningCaseStatus.CREATED);
  }
}
