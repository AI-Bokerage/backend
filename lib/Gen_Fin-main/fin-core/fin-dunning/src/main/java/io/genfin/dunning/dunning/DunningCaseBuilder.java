package io.genfin.dunning.dunning;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.statemachine.StateMachine;
import io.genfin.api.time.ClockProviders;
import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.id.DunningCaseId;
import io.genfin.dunning.lifecycle.DunningCaseEvent;
import io.genfin.dunning.lifecycle.DunningCaseStatus;
import io.genfin.dunning.obligation.FinancialObligation;

/**
 * Builds {@link DunningCase} aggregates. Preferred over the canonical constructor for readability
 * at call sites; defaults the id, clock, and lifecycle so a caller need only supply the obligation
 * and its resolved {@link CollectionPlan}. Mirrors {@code io.genfin.ledger.ledger.LedgerBuilder}.
 */
public final class DunningCaseBuilder {

  private DunningCaseId id;
  private FinancialObligation obligation;
  private CollectionPlan plan;
  private ClockProvider clockProvider = ClockProviders.system();
  private StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle;

  private DunningCaseBuilder() {}

  public static DunningCaseBuilder newCase() {
    return new DunningCaseBuilder();
  }

  public DunningCaseBuilder id(DunningCaseId id) {
    this.id = id;
    return this;
  }

  public DunningCaseBuilder obligation(FinancialObligation obligation) {
    this.obligation = obligation;
    return this;
  }

  public DunningCaseBuilder plan(CollectionPlan plan) {
    this.plan = plan;
    return this;
  }

  public DunningCaseBuilder clockProvider(ClockProvider clockProvider) {
    this.clockProvider = clockProvider;
    return this;
  }

  public DunningCaseBuilder lifecycle(StateMachine<DunningCaseStatus, DunningCaseEvent> lifecycle) {
    this.lifecycle = lifecycle;
    return this;
  }

  public DunningCase build() {
    DunningCaseId caseId = id == null ? DunningCaseId.generate() : id;
    return lifecycle == null
        ? new DunningCase(caseId, obligation, plan, clockProvider)
        : new DunningCase(caseId, obligation, plan, clockProvider, lifecycle);
  }
}
