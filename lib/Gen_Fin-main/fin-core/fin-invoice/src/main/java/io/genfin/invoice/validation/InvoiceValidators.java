package io.genfin.invoice.validation;

import io.genfin.invoice.internal.validation.DefaultInvoiceValidator;
import io.genfin.invoice.internal.validation.rules.DuplicateLinesRule;
import io.genfin.invoice.internal.validation.rules.EmptyInvoiceRule;
import io.genfin.invoice.internal.validation.rules.InvalidDatesRule;
import io.genfin.invoice.internal.validation.rules.InvalidReferencesRule;
import io.genfin.invoice.internal.validation.rules.LineCurrencyMismatchRule;
import io.genfin.invoice.internal.validation.rules.MissingNumberRule;
import io.genfin.invoice.internal.validation.rules.NegativeAmountRule;
import io.genfin.invoice.internal.validation.rules.NegativeQuantityRule;
import io.genfin.invoice.port.validation.InvoiceValidator;
import java.util.ArrayList;
import java.util.List;

/**
 * Factory for {@link InvoiceValidator}s. {@link #standard()} carries every default rule; extend via
 * {@link #withRules}.
 */
public final class InvoiceValidators {

  private InvoiceValidators() {}

  public static List<ValidationRule> defaultRules() {
    return List.of(
        new EmptyInvoiceRule(),
        new DuplicateLinesRule(),
        new NegativeQuantityRule(),
        new NegativeAmountRule(),
        new LineCurrencyMismatchRule(),
        new MissingNumberRule(),
        new InvalidDatesRule(),
        new InvalidReferencesRule());
  }

  public static InvoiceValidator standard() {
    return new DefaultInvoiceValidator(defaultRules());
  }

  public static InvoiceValidator withRules(List<ValidationRule> additionalRules) {
    List<ValidationRule> combined = new ArrayList<>(defaultRules());
    combined.addAll(additionalRules);
    return new DefaultInvoiceValidator(combined);
  }
}
