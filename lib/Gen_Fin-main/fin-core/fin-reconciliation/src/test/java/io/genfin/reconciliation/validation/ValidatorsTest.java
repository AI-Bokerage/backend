package io.genfin.reconciliation.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.money.currency.Currency;
import io.genfin.money.currency.CurrencyFactory;
import io.genfin.money.money.Money;
import io.genfin.reconciliation.id.ReconciliationBatchId;
import io.genfin.reconciliation.id.ReconciliationId;
import io.genfin.reconciliation.id.ReconciliationItemId;
import io.genfin.reconciliation.port.validation.ReconciliationValidator;
import io.genfin.reconciliation.reconciliation.Reconciliation;
import io.genfin.reconciliation.reconciliation.ReconciliationItem;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.reference.StandardReferenceType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ValidatorsTest {

  private static final Currency USD =
      CurrencyFactory.newCurrency()
          .code("USD")
          .symbol("$")
          .displayName("US Dollar")
          .fractionDigits(2)
          .build();

  private static final ValidationContext CONTEXT = ValidationContext.at(Instant.now());

  @Test
  void standardValidatorWarnsOnAnEmptyReconciliation() {
    ReconciliationValidator validator = Validators.standard();
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());

    ValidationResult result = validator.validate(reconciliation, CONTEXT);

    assertThat(result.isValid()).isTrue();
    assertThat(result.issues())
        .extracting(ValidationIssue::ruleCode)
        .contains("EMPTY_RECONCILIATION");
  }

  @Test
  void standardValidatorFlagsUnresolvedDiscrepanciesAsAnError() {
    Reconciliation reconciliation =
        new Reconciliation(ReconciliationId.generate(), ReconciliationBatchId.generate());
    reconciliation.addItem(
        new ReconciliationItem(
            ReconciliationItemId.generate(),
            Reference.of(StandardReferenceType.PAYMENT, "pay-1"),
            Money.of(new BigDecimal("100"), USD)));
    reconciliation.recordDiscrepancy("amount mismatch", ClockProviders.system());

    ValidationResult result = Validators.standard().validate(reconciliation, CONTEXT);

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues())
        .extracting(ValidationIssue::ruleCode)
        .contains("UNRESOLVED_DISCREPANCY");
  }
}
