package io.genfin.ledger.config;

import io.genfin.ledger.account.AccountTypeRegistries;
import io.genfin.ledger.balance.BalanceCalculators;
import io.genfin.ledger.journal.JournalBuilders;
import io.genfin.ledger.lifecycle.LedgerLifecycles;
import io.genfin.ledger.period.PeriodCalculators;
import io.genfin.ledger.posting.PostingEngines;
import io.genfin.ledger.posting.PostingPolicies;
import io.genfin.ledger.posting.PostingRuleRegistries;
import io.genfin.ledger.posting.PostingRuleResolvers;
import io.genfin.ledger.posting.PostingValidators;
import io.genfin.ledger.report.ReportFormatters;
import io.genfin.ledger.reversal.ReversalPolicies;
import io.genfin.ledger.reversal.ReverseJournals;
import io.genfin.ledger.trialbalance.TrialBalanceCalculators;
import io.genfin.ledger.validation.Validators;

/**
 * Factory for the default {@link LedgerConfiguration}, mirroring {@code
 * ReconciliationConfigurations}. The posting policy starts {@link PostingPolicies#empty() empty} -
 * Gen-Fin defines no built-in account names or posting mappings, so a consuming application must
 * register its own {@link io.genfin.ledger.port.posting.PostingStrategy}s before posting a fact.
 */
public final class LedgerConfigurations {

  private LedgerConfigurations() {}

  public static LedgerConfiguration standard() {
    return LedgerConfiguration.builder()
        .lifecycleProvider(LedgerLifecycles.standard())
        .accountTypeRegistry(AccountTypeRegistries.empty())
        .journalBuilderProvider(JournalBuilders.standard())
        .reversalPolicy(ReversalPolicies.standard())
        .reverseJournal(ReverseJournals.standard())
        .postingConfiguration(
            PostingConfiguration.builder()
                .policy(PostingPolicies.empty())
                .validator(PostingValidators.standard())
                .ruleRegistry(PostingRuleRegistries.empty())
                .ruleResolver(PostingRuleResolvers.of(PostingRuleRegistries.empty()))
                .engine(PostingEngines.of(PostingPolicies.empty(), PostingValidators.standard()))
                .build())
        .balanceConfiguration(
            BalanceConfiguration.builder()
                .policy(BalanceCalculators.standardPolicy())
                .calculator(BalanceCalculators.of(BalanceCalculators.standardPolicy()))
                .build())
        .periodConfiguration(
            PeriodConfiguration.builder()
                .policy(PeriodCalculators.standardPolicy())
                .calculator(PeriodCalculators.of(PeriodCalculators.standardPolicy()))
                .build())
        .reportingConfiguration(
            ReportingConfiguration.builder()
                .trialBalanceCalculator(TrialBalanceCalculators.standard())
                .formatter(ReportFormatters.standard())
                .build())
        .validationConfiguration(
            ValidationConfiguration.builder().journalValidator(Validators.standard()).build())
        .build();
  }
}
