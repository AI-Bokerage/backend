package io.genfin.dunning.schedule;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.calendar.GracePeriod;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.retry.RetryPolicies;
import java.util.List;

/**
 * Immutable, builder-based configuration for a {@code SchedulePolicy}. Every builder default below
 * exists only so {@code SchedulePolicyConfiguration.builder().build()} compiles and is testable out
 * of the box - resolving the real policy for a deployment always flows through an application's own
 * {@code DunningPolicy}, never these literals.
 */
public final class SchedulePolicyConfiguration {

  private final GracePeriod gracePeriod;
  private final RetryPolicy retryPolicy;
  private final List<BackoffInterval> reminderOffsets;

  private SchedulePolicyConfiguration(Builder builder) {
    this.gracePeriod = Validate.notNull(builder.gracePeriod, "gracePeriod must not be null.");
    this.retryPolicy = Validate.notNull(builder.retryPolicy, "retryPolicy must not be null.");
    this.reminderOffsets = List.copyOf(builder.reminderOffsets);
  }

  public static Builder builder() {
    return new Builder();
  }

  public GracePeriod gracePeriod() {
    return gracePeriod;
  }

  public RetryPolicy retryPolicy() {
    return retryPolicy;
  }

  public List<BackoffInterval> reminderOffsets() {
    return reminderOffsets;
  }

  public static final class Builder {

    private GracePeriod gracePeriod = GracePeriod.none();
    private RetryPolicy retryPolicy = RetryPolicies.standard();
    private List<BackoffInterval> reminderOffsets = List.of();

    public Builder gracePeriod(GracePeriod gracePeriod) {
      this.gracePeriod = gracePeriod;
      return this;
    }

    public Builder retryPolicy(RetryPolicy retryPolicy) {
      this.retryPolicy = retryPolicy;
      return this;
    }

    public Builder reminderOffsets(List<BackoffInterval> reminderOffsets) {
      this.reminderOffsets = reminderOffsets;
      return this;
    }

    public SchedulePolicyConfiguration build() {
      return new SchedulePolicyConfiguration(this);
    }
  }
}
