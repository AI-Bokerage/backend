package io.genfin.ledger.validation;

import io.genfin.api.spi.ExtensionRegistry;
import io.genfin.ledger.internal.validation.DefaultJournalValidator;
import io.genfin.ledger.internal.validation.rules.AccountExistsRule;
import io.genfin.ledger.internal.validation.rules.BalancedPostingRule;
import io.genfin.ledger.internal.validation.rules.CurrencyCompatibilityRule;
import io.genfin.ledger.internal.validation.rules.MoneyValidationRule;
import io.genfin.ledger.internal.validation.rules.PeriodOpenRule;
import io.genfin.ledger.internal.validation.rules.PostingRuleMatchedRule;
import io.genfin.ledger.internal.validation.rules.ReferenceValidationRule;
import io.genfin.ledger.port.validation.JournalValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link JournalValidator}s. {@link #standard()} carries every default rule; extend via
 * {@link #withRules}. Mirrors {@code io.genfin.reconciliation.validation.Validators}.
 */
public final class Validators {

  private Validators() {}

  public static List<ValidationRule> defaultRules() {
    return List.of(
        new BalancedPostingRule(),
        new AccountExistsRule(),
        new PeriodOpenRule(),
        new CurrencyCompatibilityRule(),
        new MoneyValidationRule(),
        new ReferenceValidationRule(),
        new PostingRuleMatchedRule());
  }

  public static JournalValidator standard() {
    return new DefaultJournalValidator(defaultRules());
  }

  public static JournalValidator withRules(List<ValidationRule> additionalRules) {
    List<ValidationRule> combined = new ArrayList<>(defaultRules());
    combined.addAll(additionalRules);
    return new DefaultJournalValidator(combined);
  }

  public static JournalValidator from(ExtensionRegistry registry) {
    return registry.find(JournalValidator.class).orElseGet(Validators::standard);
  }
}
