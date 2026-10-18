package io.genfin.dunning.collection;

import io.genfin.dunning.id.CollectionPlanId;
import io.genfin.dunning.id.DunningPolicyId;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link CollectionPlan}s, letting callers append {@link CollectionStage}s one at a time
 * instead of assembling a {@code List} up front. Prefer {@link CollectionPlan#standard(
 * DunningPolicyId)} for the conventional reminder/retry/escalate pipeline; use this builder only
 * when a policy resolves a custom stage order or subset.
 */
public final class CollectionPlanBuilder {

  private CollectionPlanId id;
  private DunningPolicyId policyId;
  private final List<CollectionStage> stages = new ArrayList<>();

  private CollectionPlanBuilder() {}

  public static CollectionPlanBuilder newPlan() {
    return new CollectionPlanBuilder();
  }

  public CollectionPlanBuilder id(CollectionPlanId id) {
    this.id = id;
    return this;
  }

  public CollectionPlanBuilder policyId(DunningPolicyId policyId) {
    this.policyId = policyId;
    return this;
  }

  public CollectionPlanBuilder stage(CollectionStage stage) {
    stages.add(stage);
    return this;
  }

  public CollectionPlanBuilder stages(List<CollectionStage> stages) {
    this.stages.addAll(stages);
    return this;
  }

  public CollectionPlan build() {
    return new CollectionPlan(
        id == null ? CollectionPlanId.generate() : id, policyId, List.copyOf(stages));
  }
}
