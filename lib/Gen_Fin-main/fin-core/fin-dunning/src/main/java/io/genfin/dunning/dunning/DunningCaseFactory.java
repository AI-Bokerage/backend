package io.genfin.dunning.dunning;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.api.time.ClockProviders;
import io.genfin.dunning.collection.CollectionPlan;
import io.genfin.dunning.lifecycle.DunningCaseLifecycles;
import io.genfin.dunning.lifecycle.StandardDunningCaseStatus;
import io.genfin.dunning.obligation.FinancialObligation;
import io.genfin.dunning.port.lifecycle.DunningCaseLifecycleProvider;

/**
 * Creates {@link DunningCase}s wired to the {@link DunningCaseLifecycleProvider} registered in an
 * {@link ExtensionRegistry}, falling back to {@link DunningCaseLifecycles#standard()}. Preferred
 * over {@link DunningCaseBuilder} directly whenever the lifecycle should come from the deployment's
 * configured extensions rather than the built-in default. Mirrors {@code
 * io.genfin.ledger.journal.JournalFactory}.
 */
public final class DunningCaseFactory {

  private DunningCaseFactory() {}

  public static DunningCase open(
      ExtensionRegistry registry, FinancialObligation obligation, CollectionPlan plan) {
    return open(registry, obligation, plan, ClockProviders.system());
  }

  public static DunningCase open(
      ExtensionRegistry registry,
      FinancialObligation obligation,
      CollectionPlan plan,
      ClockProvider clockProvider) {
    DunningCaseLifecycleProvider lifecycleProvider =
        registry
            .find(DunningCaseLifecycleProvider.class)
            .orElseGet(DunningCaseLifecycles::standard);
    return DunningCaseBuilder.newCase()
        .obligation(obligation)
        .plan(plan)
        .clockProvider(clockProvider)
        .lifecycle(lifecycleProvider.create(StandardDunningCaseStatus.CREATED))
        .build();
  }
}
