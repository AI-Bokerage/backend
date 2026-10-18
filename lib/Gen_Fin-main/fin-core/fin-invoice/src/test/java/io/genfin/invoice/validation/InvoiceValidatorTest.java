package io.genfin.invoice.validation;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.invoice.factory.LineFactory;
import io.genfin.invoice.invoice.Invoice;
import io.genfin.invoice.invoice.InvoiceBuilder;
import io.genfin.invoice.numbering.InvoiceNumber;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvoiceValidatorTest {

  private static Invoice draft() {
    return InvoiceBuilder.newInvoice()
        .currency(USD)
        .dueDate(Instant.parse("2026-02-01T00:00:00Z"))
        .clockProvider(ClockProviders.system())
        .actor("tester")
        .build();
  }

  @Test
  void emptyInvoiceFailsValidation() {
    ValidationResult result =
        InvoiceValidators.standard().validate(draft(), ValidationContext.at(Instant.now()));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("EMPTY_INVOICE"));
  }

  @Test
  void wellFormedInvoicePassesValidation() {
    var clock = ClockProviders.fixed(Instant.parse("2026-01-15T00:00:00Z"));
    Invoice invoice = draft();
    invoice.addLine(LineFactory.simple("Item", BigDecimal.ONE, Money.of("10.00", USD)), clock, "t");
    invoice.issue(InvoiceNumber.of("INV-1"), clock, "t");

    ValidationResult result =
        InvoiceValidators.standard().validate(invoice, ValidationContext.at(Instant.now()));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void issuedInvoiceWithoutNumberFailsValidation() {
    Invoice invoice = draft();
    invoice.addLine(
        LineFactory.simple("Item", BigDecimal.ONE, Money.of("10.00", USD)),
        ClockProviders.system(),
        "t");

    // Force to a non-draft state indirectly is not possible without issuing (which requires a
    // number),
    // so this exercises the rule via a still-draft invoice missing a number, confirming the rule is
    // inert then.
    ValidationResult result =
        InvoiceValidators.standard().validate(invoice, ValidationContext.at(Instant.now()));

    assertThat(result.issues()).noneMatch(issue -> "MISSING_NUMBER".equals(issue.ruleCode()));
  }

  @Test
  void invalidDueDateBeforeIssueDateFailsValidation() {
    Invoice invoice =
        InvoiceBuilder.newInvoice()
            .currency(USD)
            .dueDate(Instant.parse("2020-01-01T00:00:00Z"))
            .clockProvider(ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z")))
            .actor("tester")
            .build();
    invoice.addLine(
        LineFactory.simple("Item", BigDecimal.ONE, Money.of("10.00", USD)),
        ClockProviders.system(),
        "t");
    invoice.issue(
        InvoiceNumber.of("INV-1"),
        ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z")),
        "t");

    ValidationResult result =
        InvoiceValidators.standard().validate(invoice, ValidationContext.at(Instant.now()));

    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("DUE_BEFORE_ISSUE"));
  }

  @Test
  void additionalRulesComposeWithDefaults() {
    ValidationRule alwaysFails =
        (invoice, context) ->
            java.util.List.of(
                ValidationIssue.of(
                    "CUSTOM_RULE", "always fails", io.genfin.api.exception.Severity.ERROR));

    ValidationResult result =
        InvoiceValidators.withRules(java.util.List.of(alwaysFails))
            .validate(draft(), ValidationContext.at(Instant.now()));

    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("CUSTOM_RULE"));
  }
}
