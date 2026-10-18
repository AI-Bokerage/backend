package io.genfin.dunning.config;

import io.genfin.api.validation.Validate;
import io.genfin.dunning.failure.FailurePolicies;
import io.genfin.dunning.lifecycle.DunningCaseLifecycles;
import io.genfin.dunning.notification.NotificationStrategies;
import io.genfin.dunning.policy.DunningPolicies;
import io.genfin.dunning.port.failure.FailurePolicy;
import io.genfin.dunning.port.lifecycle.DunningCaseLifecycleProvider;
import io.genfin.dunning.port.notification.NotificationStrategy;
import io.genfin.dunning.port.policy.DunningPolicy;
import io.genfin.dunning.port.rule.CollectionRuleEngine;
import io.genfin.dunning.rule.CollectionRuleEngines;

/**
 * The full, explicit policy set for a fin-dunning deployment: the cross-cutting collaborators
 * (resolved {@code DunningPolicy}, lifecycle provider, collection rule engine, failure policy,
 * notification strategy) plus the composed sub-configurations for the concerns that carry their own
 * collaborator (Backoff, Calendar, Retry, Schedule, Reminder, Escalation, Validation). Mirrors
 * {@code io.genfin.ledger.config.LedgerConfiguration} / {@code
 * io.genfin.pricing.config.PricingConfiguration}.
 */
public final class DunningConfiguration {

  private final DunningPolicy dunningPolicy;
  private final DunningCaseLifecycleProvider lifecycleProvider;
  private final CollectionRuleEngine collectionRuleEngine;
  private final FailurePolicy failurePolicy;
  private final NotificationStrategy notificationStrategy;
  private final BackoffConfiguration backoffConfiguration;
  private final CalendarConfiguration calendarConfiguration;
  private final RetryConfiguration retryConfiguration;
  private final ScheduleConfiguration scheduleConfiguration;
  private final ReminderConfiguration reminderConfiguration;
  private final EscalationConfiguration escalationConfiguration;
  private final ValidationConfiguration validationConfiguration;

  private DunningConfiguration(Builder builder) {
    this.dunningPolicy = Validate.notNull(builder.dunningPolicy, "dunningPolicy must not be null.");
    this.lifecycleProvider =
        Validate.notNull(builder.lifecycleProvider, "lifecycleProvider must not be null.");
    this.collectionRuleEngine =
        Validate.notNull(builder.collectionRuleEngine, "collectionRuleEngine must not be null.");
    this.failurePolicy = Validate.notNull(builder.failurePolicy, "failurePolicy must not be null.");
    this.notificationStrategy =
        Validate.notNull(builder.notificationStrategy, "notificationStrategy must not be null.");
    this.backoffConfiguration =
        Validate.notNull(builder.backoffConfiguration, "backoffConfiguration must not be null.");
    this.calendarConfiguration =
        Validate.notNull(builder.calendarConfiguration, "calendarConfiguration must not be null.");
    this.retryConfiguration =
        Validate.notNull(builder.retryConfiguration, "retryConfiguration must not be null.");
    this.scheduleConfiguration =
        Validate.notNull(builder.scheduleConfiguration, "scheduleConfiguration must not be null.");
    this.reminderConfiguration =
        Validate.notNull(builder.reminderConfiguration, "reminderConfiguration must not be null.");
    this.escalationConfiguration =
        Validate.notNull(
            builder.escalationConfiguration, "escalationConfiguration must not be null.");
    this.validationConfiguration =
        Validate.notNull(
            builder.validationConfiguration, "validationConfiguration must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public DunningPolicy dunningPolicy() {
    return dunningPolicy;
  }

  public DunningCaseLifecycleProvider lifecycleProvider() {
    return lifecycleProvider;
  }

  public CollectionRuleEngine collectionRuleEngine() {
    return collectionRuleEngine;
  }

  public FailurePolicy failurePolicy() {
    return failurePolicy;
  }

  public NotificationStrategy notificationStrategy() {
    return notificationStrategy;
  }

  public BackoffConfiguration backoffConfiguration() {
    return backoffConfiguration;
  }

  public CalendarConfiguration calendarConfiguration() {
    return calendarConfiguration;
  }

  public RetryConfiguration retryConfiguration() {
    return retryConfiguration;
  }

  public ScheduleConfiguration scheduleConfiguration() {
    return scheduleConfiguration;
  }

  public ReminderConfiguration reminderConfiguration() {
    return reminderConfiguration;
  }

  public EscalationConfiguration escalationConfiguration() {
    return escalationConfiguration;
  }

  public ValidationConfiguration validationConfiguration() {
    return validationConfiguration;
  }

  public static final class Builder {

    private DunningPolicy dunningPolicy = DunningPolicies.standard();
    private DunningCaseLifecycleProvider lifecycleProvider = DunningCaseLifecycles.standard();
    private CollectionRuleEngine collectionRuleEngine = CollectionRuleEngines.standard();
    private FailurePolicy failurePolicy = FailurePolicies.standard();
    private NotificationStrategy notificationStrategy = NotificationStrategies.standard();
    private BackoffConfiguration backoffConfiguration = BackoffConfiguration.builder().build();
    private CalendarConfiguration calendarConfiguration = CalendarConfiguration.builder().build();
    private RetryConfiguration retryConfiguration = RetryConfiguration.builder().build();
    private ScheduleConfiguration scheduleConfiguration = ScheduleConfiguration.builder().build();
    private ReminderConfiguration reminderConfiguration = ReminderConfiguration.builder().build();
    private EscalationConfiguration escalationConfiguration =
        EscalationConfiguration.builder().build();
    private ValidationConfiguration validationConfiguration =
        ValidationConfiguration.builder().build();

    public Builder dunningPolicy(DunningPolicy dunningPolicy) {
      this.dunningPolicy = dunningPolicy;
      return this;
    }

    public Builder lifecycleProvider(DunningCaseLifecycleProvider lifecycleProvider) {
      this.lifecycleProvider = lifecycleProvider;
      return this;
    }

    public Builder collectionRuleEngine(CollectionRuleEngine collectionRuleEngine) {
      this.collectionRuleEngine = collectionRuleEngine;
      return this;
    }

    public Builder failurePolicy(FailurePolicy failurePolicy) {
      this.failurePolicy = failurePolicy;
      return this;
    }

    public Builder notificationStrategy(NotificationStrategy notificationStrategy) {
      this.notificationStrategy = notificationStrategy;
      return this;
    }

    public Builder backoffConfiguration(BackoffConfiguration backoffConfiguration) {
      this.backoffConfiguration = backoffConfiguration;
      return this;
    }

    public Builder calendarConfiguration(CalendarConfiguration calendarConfiguration) {
      this.calendarConfiguration = calendarConfiguration;
      return this;
    }

    public Builder retryConfiguration(RetryConfiguration retryConfiguration) {
      this.retryConfiguration = retryConfiguration;
      return this;
    }

    public Builder scheduleConfiguration(ScheduleConfiguration scheduleConfiguration) {
      this.scheduleConfiguration = scheduleConfiguration;
      return this;
    }

    public Builder reminderConfiguration(ReminderConfiguration reminderConfiguration) {
      this.reminderConfiguration = reminderConfiguration;
      return this;
    }

    public Builder escalationConfiguration(EscalationConfiguration escalationConfiguration) {
      this.escalationConfiguration = escalationConfiguration;
      return this;
    }

    public Builder validationConfiguration(ValidationConfiguration validationConfiguration) {
      this.validationConfiguration = validationConfiguration;
      return this;
    }

    public DunningConfiguration build() {
      return new DunningConfiguration(this);
    }
  }
}
