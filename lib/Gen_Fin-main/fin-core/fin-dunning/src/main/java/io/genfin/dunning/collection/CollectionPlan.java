package io.genfin.dunning.collection;

import io.genfin.api.domain.ValueObject;
import io.genfin.api.validation.Validate;
import io.genfin.dunning.id.CollectionPlanId;
import io.genfin.dunning.id.DunningPolicyId;
import java.util.List;

/**
 * The ordered sequence of {@link CollectionStage}s a {@code DunningCase} is expected to progress
 * through, as resolved from a {@code DunningPolicy}. Carries only the policy identity and the
 * resolved stage order - never a retry count, interval, or channel itself, those live in the {@code
 * ReminderPlan}/{@code RetryPlan}/{@code EscalationPlan} produced for each stage.
 */
public record CollectionPlan(
    CollectionPlanId id, DunningPolicyId policyId, List<CollectionStage> stages)
    implements ValueObject {

  public CollectionPlan {
    Validate.notNull(id, "id must not be null.");
    Validate.notNull(policyId, "policyId must not be null.");
    Validate.required(stages != null && !stages.isEmpty(), "stages must not be empty.");
    stages = List.copyOf(stages);
  }

  /**
   * The conventional full pipeline: reminders, then retries, then escalation. An application is
   * free to resolve any other stage order or subset from its own {@code DunningPolicy}.
   */
  public static CollectionPlan standard(DunningPolicyId policyId) {
    return new CollectionPlan(
        CollectionPlanId.generate(),
        policyId,
        List.of(CollectionStage.REMINDING, CollectionStage.RETRYING, CollectionStage.ESCALATING));
  }

  public CollectionStage firstStage() {
    return stages.getFirst();
  }

  public boolean includes(CollectionStage stage) {
    return stages.contains(stage);
  }
}
