package io.genfin.dunning.internal.policy;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.collection.CollectionStage;
import io.genfin.dunning.id.DunningPolicyId;
import io.genfin.dunning.policy.PolicyVersion;
import io.genfin.dunning.port.escalation.EscalationPolicy;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.reminder.ReminderPolicy;
import io.genfin.dunning.port.schedule.SchedulePolicy;
import java.util.List;

public final class DefaultDunningPolicy implements DunningPolicy {

  private final DunningPolicyId id;
  private final PolicyVersion version;
  private final SchedulePolicy schedulePolicy;
  private final ReminderPolicy reminderPolicy;
  private final EscalationPolicy escalationPolicy;
  private final List<CollectionStage> stages;

  public DefaultDunningPolicy(
      DunningPolicyId id,
      PolicyVersion version,
      SchedulePolicy schedulePolicy,
      ReminderPolicy reminderPolicy,
      EscalationPolicy escalationPolicy,
      List<CollectionStage> stages) {
    this.id = Validate.notNull(id, "id must not be null.");
    this.version = Validate.notNull(version, "version must not be null.");
    this.schedulePolicy = Validate.notNull(schedulePolicy, "schedulePolicy must not be null.");
    this.reminderPolicy = Validate.notNull(reminderPolicy, "reminderPolicy must not be null.");
    this.escalationPolicy =
        Validate.notNull(escalationPolicy, "escalationPolicy must not be null.");
    Validate.required(stages != null && !stages.isEmpty(), "stages must not be empty.");
    this.stages = List.copyOf(stages);
  }

  @Override
  public DunningPolicyId id() {
    return id;
  }

  @Override
  public PolicyVersion version() {
    return version;
  }

  @Override
  public SchedulePolicy schedulePolicy() {
    return schedulePolicy;
  }

  @Override
  public ReminderPolicy reminderPolicy() {
    return reminderPolicy;
  }

  @Override
  public EscalationPolicy escalationPolicy() {
    return escalationPolicy;
  }

  @Override
  public List<CollectionStage> stages() {
    return stages;
  }
}
