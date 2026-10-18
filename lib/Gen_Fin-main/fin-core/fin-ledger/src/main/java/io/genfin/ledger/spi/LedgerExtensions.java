package io.genfin.ledger.spi;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.ledger.account.AccountTypeRegistries;
import io.genfin.ledger.balance.BalanceCalculators;
import io.genfin.ledger.calculation.AccountCalculators;
import io.genfin.ledger.calculation.VarianceCalculators;
import io.genfin.ledger.journal.JournalBuilders;
import io.genfin.ledger.lifecycle.LedgerLifecycles;
import io.genfin.ledger.period.PeriodCalculators;
import io.genfin.ledger.port.account.AccountTypeRegistry;
import io.genfin.ledger.port.balance.BalanceCalculator;
import io.genfin.ledger.port.balance.BalancePolicy;
import io.genfin.ledger.port.calculation.AccountCalculator;
import io.genfin.ledger.port.calculation.VarianceCalculator;
import io.genfin.ledger.port.journal.JournalBuilderProvider;
import io.genfin.ledger.port.lifecycle.LedgerLifecycleProvider;
import io.genfin.ledger.port.period.PeriodCalculator;
import io.genfin.ledger.port.period.PeriodPolicy;
import io.genfin.ledger.port.posting.PostingEngine;
import io.genfin.ledger.port.posting.PostingPolicy;
import io.genfin.ledger.port.posting.PostingRuleRegistry;
import io.genfin.ledger.port.posting.PostingRuleResolver;
import io.genfin.ledger.port.posting.PostingValidator;
import io.genfin.ledger.port.report.ReportFormatter;
import io.genfin.ledger.port.reversal.ReversalPolicy;
import io.genfin.ledger.port.reversal.ReversalReasonProvider;
import io.genfin.ledger.port.trialbalance.TrialBalanceCalculator;
import io.genfin.ledger.port.validation.JournalValidator;
import io.genfin.ledger.posting.PostingEngines;
import io.genfin.ledger.posting.PostingPolicies;
import io.genfin.ledger.posting.PostingRuleRegistries;
import io.genfin.ledger.posting.PostingRuleResolvers;
import io.genfin.ledger.posting.PostingValidators;
import io.genfin.ledger.report.ReportFormatters;
import io.genfin.ledger.reversal.ReversalPolicies;
import io.genfin.ledger.reversal.ReversalReasonRegistries;
import io.genfin.ledger.trialbalance.TrialBalanceCalculators;
import io.genfin.ledger.validation.ValidationRule;
import io.genfin.ledger.validation.Validators;

/**
 * Registers every default Ledger-engine extension so downstream code discovers them through one
 * mechanism. Mirrors {@code io.genfin.reconciliation.spi.ReconciliationExtensions}.
 *
 * <p>Account types and posting rule mappings have no built-in catalog - Gen-Fin never hardcodes
 * business account names or posting mappings - so {@link AccountTypeRegistry} and {@link
 * PostingRuleRegistry}/{@link PostingPolicy} are registered empty; a consuming application
 * overrides them with its own via {@link ExtensionRegistry#register}.
 */
public final class LedgerExtensions {

  private LedgerExtensions() {}

  public static void registerDefaults(ExtensionRegistry registry) {
    registry.register(LedgerLifecycleProvider.class, LedgerLifecycles.standard());
    registry.register(JournalBuilderProvider.class, JournalBuilders.standard());
    registry.register(AccountTypeRegistry.class, AccountTypeRegistries.empty());
    registry.register(AccountCalculator.class, AccountCalculators.standard());
    registry.register(VarianceCalculator.class, VarianceCalculators.standard());

    registerPosting(registry);

    BalancePolicy balancePolicy = BalanceCalculators.standardPolicy();
    registry.register(BalancePolicy.class, balancePolicy);
    registry.register(BalanceCalculator.class, BalanceCalculators.of(balancePolicy));

    PeriodPolicy periodPolicy = PeriodCalculators.standardPolicy();
    registry.register(PeriodPolicy.class, periodPolicy);
    registry.register(PeriodCalculator.class, PeriodCalculators.of(periodPolicy));

    registry.register(TrialBalanceCalculator.class, TrialBalanceCalculators.standard());

    registry.register(ReversalReasonProvider.class, ReversalReasonRegistries.standardCatalog());
    registry.register(ReversalPolicy.class, ReversalPolicies.standard());

    registerValidationRules(registry);
    registry.register(JournalValidator.class, Validators.standard());

    registry.register(ReportFormatter.class, ReportFormatters.standard());
  }

  private static void registerPosting(ExtensionRegistry registry) {
    PostingRuleRegistry ruleRegistry = PostingRuleRegistries.empty();
    registry.register(PostingRuleRegistry.class, ruleRegistry);
    registry.register(PostingRuleResolver.class, PostingRuleResolvers.of(ruleRegistry));

    PostingPolicy postingPolicy = PostingPolicies.empty();
    registry.register(PostingPolicy.class, postingPolicy);

    PostingValidator postingValidator = PostingValidators.standard();
    registry.register(PostingValidator.class, postingValidator);

    registry.register(PostingEngine.class, PostingEngines.of(postingPolicy, postingValidator));
  }

  private static void registerValidationRules(ExtensionRegistry registry) {
    Validators.defaultRules().forEach(rule -> registry.register(ValidationRule.class, rule));
  }
}
