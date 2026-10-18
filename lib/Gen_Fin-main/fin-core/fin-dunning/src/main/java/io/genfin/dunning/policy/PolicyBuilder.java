package io.genfin.dunning.policy;

import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.escalation.EscalationPolicies;
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.internal.policy.DefaultDunningPolicy;
import io.genfin.dunning.port.escalation.EscalationPolicy;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.reminder.ReminderPolicy;
import io.genfin.dunning.port.schedule.SchedulePolicy;
import io.genfin.dunning.reminder.ReminderPolicies;
import io.genfin.dunning.schedule.SchedulePolicies;
import java.util.List;

/**
 * Fluent builder assembling a {@link DunningPolicy} out of its composed sub-policies. Every default
 * below exists only so {@code PolicyBuilder.create().build()} compiles and is testable out of the
 * box - a real deployment always supplies its own {@link SchedulePolicy}/{@link ReminderPolicy}/
 * {@link EscalationPolicy}, resolved from that application's own rules, never these literals.
 */
public final class PolicyBuilder {

  /**
   * The conventional full pipeline: reminders, then retries, then escalation. An application may
   * configure any other stage order or subset.
   */
  private static final List<CollectionStage> DEFAULT_STAGES =
      List.of(CollectionStage.REMINDING, CollectionStage.RETRYING, CollectionStage.ESCALATING);

  private DunningPolicyId id = DunningPolicyId.generate();
  private PolicyVersion version = PolicyVersion.initial();
  private SchedulePolicy schedulePolicy = SchedulePolicies.standard();
  private ReminderPolicy reminderPolicy = ReminderPolicies.standard();
  private EscalationPolicy escalationPolicy = EscalationPolicies.standard();
  private List<CollectionStage> stages = DEFAULT_STAGES;

  private PolicyBuilder() {}

  public static PolicyBuilder create() {
    return new PolicyBuilder();
  }

  public PolicyBuilder id(DunningPolicyId id) {
    this.id = id;
    return this;
  }

  public PolicyBuilder version(PolicyVersion version) {
    this.version = version;
    return this;
  }

  public PolicyBuilder schedulePolicy(SchedulePolicy schedulePolicy) {
    this.schedulePolicy = schedulePolicy;
    return this;
  }

  public PolicyBuilder reminderPolicy(ReminderPolicy reminderPolicy) {
    this.reminderPolicy = reminderPolicy;
    return this;
  }

  public PolicyBuilder escalationPolicy(EscalationPolicy escalationPolicy) {
    this.escalationPolicy = escalationPolicy;
    return this;
  }

  public PolicyBuilder stages(List<CollectionStage> stages) {
    this.stages = stages;
    return this;
  }

  public DunningPolicy build() {
    return new DefaultDunningPolicy(
        id, version, schedulePolicy, reminderPolicy, escalationPolicy, stages);
  }
}
