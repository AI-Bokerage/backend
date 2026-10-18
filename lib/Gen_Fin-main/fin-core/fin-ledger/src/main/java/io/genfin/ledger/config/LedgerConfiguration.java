package io.genfin.ledger.config;

import io.genfin.api.validation.Validate;
import io.genfin.ledger.port.account.AccountTypeRegistry;
import io.genfin.ledger.port.journal.JournalBuilderProvider;
import io.genfin.ledger.port.lifecycle.LedgerLifecycleProvider;
import io.genfin.ledger.port.reversal.ReversalPolicy;
import io.genfin.ledger.port.reversal.ReverseJournal;

/**
 * The full, explicit policy set for a Ledger-engine deployment, mirroring {@code
 * ReconciliationConfiguration}: the cross-cutting policies (lifecycle, chart of accounts, journal
 * building, reversal) plus the composed sub-configurations for the concerns that carry more than
 * one collaborator each.
 */
public final class LedgerConfiguration {

  private final LedgerLifecycleProvider lifecycleProvider;
  private final AccountTypeRegistry accountTypeRegistry;
  private final JournalBuilderProvider journalBuilderProvider;
  private final ReversalPolicy reversalPolicy;
  private final ReverseJournal reverseJournal;
  private final PostingConfiguration postingConfiguration;
  private final BalanceConfiguration balanceConfiguration;
  private final PeriodConfiguration periodConfiguration;
  private final ReportingConfiguration reportingConfiguration;
  private final ValidationConfiguration validationConfiguration;

  private LedgerConfiguration(Builder builder) {
    this.lifecycleProvider =
        Validate.notNull(builder.lifecycleProvider, "lifecycleProvider must not be null.");
    this.accountTypeRegistry =
        Validate.notNull(builder.accountTypeRegistry, "accountTypeRegistry must not be null.");
    this.journalBuilderProvider =
        Validate.notNull(
            builder.journalBuilderProvider, "journalBuilderProvider must not be null.");
    this.reversalPolicy =
        Validate.notNull(builder.reversalPolicy, "reversalPolicy must not be null.");
    this.reverseJournal =
        Validate.notNull(builder.reverseJournal, "reverseJournal must not be null.");
    this.postingConfiguration =
        Validate.notNull(builder.postingConfiguration, "postingConfiguration must not be null.");
    this.balanceConfiguration =
        Validate.notNull(builder.balanceConfiguration, "balanceConfiguration must not be null.");
    this.periodConfiguration =
        Validate.notNull(builder.periodConfiguration, "periodConfiguration must not be null.");
    this.reportingConfiguration =
        Validate.notNull(
            builder.reportingConfiguration, "reportingConfiguration must not be null.");
    this.validationConfiguration =
        Validate.notNull(
            builder.validationConfiguration, "validationConfiguration must not be null.");
  }

  public static Builder builder() {
    return new Builder();
  }

  public LedgerLifecycleProvider lifecycleProvider() {
    return lifecycleProvider;
  }

  public AccountTypeRegistry accountTypeRegistry() {
    return accountTypeRegistry;
  }

  public JournalBuilderProvider journalBuilderProvider() {
    return journalBuilderProvider;
  }

  public ReversalPolicy reversalPolicy() {
    return reversalPolicy;
  }

  public ReverseJournal reverseJournal() {
    return reverseJournal;
  }

  public PostingConfiguration postingConfiguration() {
    return postingConfiguration;
  }

  public BalanceConfiguration balanceConfiguration() {
    return balanceConfiguration;
  }

  public PeriodConfiguration periodConfiguration() {
    return periodConfiguration;
  }

  public ReportingConfiguration reportingConfiguration() {
    return reportingConfiguration;
  }

  public ValidationConfiguration validationConfiguration() {
    return validationConfiguration;
  }

  public static final class Builder {

    private LedgerLifecycleProvider lifecycleProvider;
    private AccountTypeRegistry accountTypeRegistry;
    private JournalBuilderProvider journalBuilderProvider;
    private ReversalPolicy reversalPolicy;
    private ReverseJournal reverseJournal;
    private PostingConfiguration postingConfiguration;
    private BalanceConfiguration balanceConfiguration;
    private PeriodConfiguration periodConfiguration;
    private ReportingConfiguration reportingConfiguration;
    private ValidationConfiguration validationConfiguration;

    public Builder lifecycleProvider(LedgerLifecycleProvider lifecycleProvider) {
      this.lifecycleProvider = lifecycleProvider;
      return this;
    }

    public Builder accountTypeRegistry(AccountTypeRegistry accountTypeRegistry) {
      this.accountTypeRegistry = accountTypeRegistry;
      return this;
    }

    public Builder journalBuilderProvider(JournalBuilderProvider journalBuilderProvider) {
      this.journalBuilderProvider = journalBuilderProvider;
      return this;
    }

    public Builder reversalPolicy(ReversalPolicy reversalPolicy) {
      this.reversalPolicy = reversalPolicy;
      return this;
    }

    public Builder reverseJournal(ReverseJournal reverseJournal) {
      this.reverseJournal = reverseJournal;
      return this;
    }

    public Builder postingConfiguration(PostingConfiguration postingConfiguration) {
      this.postingConfiguration = postingConfiguration;
      return this;
    }

    public Builder balanceConfiguration(BalanceConfiguration balanceConfiguration) {
      this.balanceConfiguration = balanceConfiguration;
      return this;
    }

    public Builder periodConfiguration(PeriodConfiguration periodConfiguration) {
      this.periodConfiguration = periodConfiguration;
      return this;
    }

    public Builder reportingConfiguration(ReportingConfiguration reportingConfiguration) {
      this.reportingConfiguration = reportingConfiguration;
      return this;
    }

    public Builder validationConfiguration(ValidationConfiguration validationConfiguration) {
      this.validationConfiguration = validationConfiguration;
      return this;
    }

    public LedgerConfiguration build() {
      return new LedgerConfiguration(this);
    }
  }
}
