package io.genfin.dunning.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.dunning.backoff.BackoffInterval;
import io.genfin.dunning.backoff.BackoffStrategies;
import io.genfin.dunning.calendar.BusinessCalendars;
import io.genfin.dunning.calendar.HolidayProviders;
import io.genfin.dunning.calendar.WeekendStrategies;
import io.genfin.dunning.escalation.EscalationPolicies;
import io.genfin.dunning.escalation.EscalationStrategies;
import io.genfin.dunning.failure.FailureClassifiers;
import io.genfin.dunning.failure.FailurePolicies;
import io.genfin.dunning.lifecycle.DunningCaseLifecycles;
import io.genfin.dunning.notification.NotificationStrategies;
import io.genfin.dunning.policy.PolicyRegistries;
import io.genfin.dunning.port.backoff.BackoffStrategy;
import io.genfin.dunning.port.calendar.BusinessCalendar;
import io.genfin.dunning.port.calendar.HolidayProvider;
import io.genfin.dunning.port.calendar.WeekendStrategy;
import io.genfin.dunning.port.escalation.EscalationPolicy;
import io.genfin.dunning.port.escalation.EscalationStrategy;
import io.genfin.dunning.port.failure.FailureClassifier;
import io.genfin.dunning.port.failure.FailurePolicy;
import io.genfin.dunning.port.lifecycle.DunningCaseLifecycleProvider;
import io.genfin.dunning.port.notification.NotificationStrategy;
import io.genfin.dunning.port.policy.PolicyRegistry;
import io.genfin.dunning.port.reminder.ReminderPolicy;
import io.genfin.dunning.port.reminder.ReminderStrategy;
import io.genfin.dunning.port.retry.RetryPolicy;
import io.genfin.dunning.port.retry.RetryStrategy;
import io.genfin.dunning.port.rule.CollectionRule;
import io.genfin.dunning.port.rule.CollectionRuleEngine;
import io.genfin.dunning.port.schedule.SchedulePolicy;
import io.genfin.dunning.port.schedule.ScheduleStrategy;
import io.genfin.dunning.port.validation.DunningValidator;
import io.genfin.dunning.reminder.ReminderPolicies;
import io.genfin.dunning.reminder.ReminderStrategies;
import io.genfin.dunning.retry.RetryPolicies;
import io.genfin.dunning.retry.RetryStrategies;
import io.genfin.dunning.rule.CollectionRuleEngines;
import io.genfin.dunning.rule.CollectionRules;
import io.genfin.dunning.schedule.SchedulePolicies;
import io.genfin.dunning.schedule.ScheduleStrategies;
import io.genfin.dunning.validation.ValidationRule;
import io.genfin.dunning.validation.Validators;
import java.time.temporal.ChronoUnit;

/**
 * Registers every default fin-dunning extension so downstream code discovers them through one
 * mechanism. Mirrors {@code io.genfin.ledger.spi.LedgerExtensions} / {@code
 * io.genfin.pricing.spi.PricingExtensions}.
 *
 * <p>{@link PolicyRegistry} is registered empty - Gen-Fin defines no built-in dunning policies, so
 * an application always registers its own {@code DunningPolicy} instances. The registered {@link
 * BackoffStrategy}, {@link RetryPolicy}, {@link SchedulePolicy}, {@link ReminderPolicy}, and {@link
 * EscalationPolicy} are all example/all-defaulted instances built from their respective {@code
 * *Configuration} builders (see each type's javadoc) - never a hardcoded interval, retry count, or
 * escalation ladder baked directly into business logic. A real deployment always resolves its
 * actual policy through a {@code DunningPolicy} it builds and registers itself, overriding any of
 * these via {@link ExtensionRegistry#register}.
 */
public final class DunningExtensions {

  private DunningExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(DunningCaseLifecycleProvider.class, DunningCaseLifecycles.standard());

    registerCalendar(registry);
    registerRetry(registry);
    registerSchedule(registry);
    registerReminder(registry);
    registry.register(NotificationStrategy.class, NotificationStrategies.standard());
    registerEscalation(registry);
    registerRules(registry);
    registerFailure(registry);

    registry.register(PolicyRegistry.class, PolicyRegistries.empty());

    registerValidation(registry);
  }

  private static void registerCalendar(ExtensionRegistry registry) {
    registry.register(HolidayProvider.class, HolidayProviders.none());
    registry.register(WeekendStrategy.class, WeekendStrategies.satSun());
    registry.register(BusinessCalendar.class, BusinessCalendars.standard());
  }

  private static void registerRetry(ExtensionRegistry registry) {
    // Example fixed-interval shape only - fixed/linear/exponential/fibonacci/custom are all
    // equally supported; an application selects and configures whichever it needs via
    // BackoffStrategies/BackoffInterval, never this default.
    registry.register(
        BackoffStrategy.class, BackoffStrategies.fixed(BackoffInterval.of(1, ChronoUnit.DAYS)));
    registry.register(RetryPolicy.class, RetryPolicies.standard());
    registry.register(RetryStrategy.class, RetryStrategies.standard());
  }

  private static void registerSchedule(ExtensionRegistry registry) {
    registry.register(SchedulePolicy.class, SchedulePolicies.standard());
    registry.register(ScheduleStrategy.class, ScheduleStrategies.standard());
  }

  private static void registerReminder(ExtensionRegistry registry) {
    registry.register(ReminderPolicy.class, ReminderPolicies.standard());
    registry.register(ReminderStrategy.class, ReminderStrategies.standard());
  }

  private static void registerEscalation(ExtensionRegistry registry) {
    registry.register(EscalationPolicy.class, EscalationPolicies.standard());
    registry.register(EscalationStrategy.class, EscalationStrategies.standard());
  }

  private static void registerRules(ExtensionRegistry registry) {
    CollectionRules.defaultRules().forEach(rule -> registry.register(CollectionRule.class, rule));
    registry.register(CollectionRuleEngine.class, CollectionRuleEngines.standard());
  }

  private static void registerFailure(ExtensionRegistry registry) {
    registry.register(FailurePolicy.class, FailurePolicies.standard());
    registry.register(FailureClassifier.class, FailureClassifiers.standard());
  }

  private static void registerValidation(ExtensionRegistry registry) {
    Validators.defaultRules().forEach(rule -> registry.register(ValidationRule.class, rule));
    registry.register(DunningValidator.class, Validators.standard());
  }
}
