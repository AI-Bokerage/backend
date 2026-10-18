package io.genfin.refund.validation;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.money.money.Money;
import io.genfin.refund.calculation.RefundBalance;
import io.genfin.refund.id.RefundId;
import io.genfin.refund.reason.RefundReasonRegistries;
import io.genfin.refund.reason.StandardRefundReason;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.refund.Refund;
import io.genfin.refund.refund.RefundDirection;
import io.genfin.refund.refund.RefundNumber;
import io.genfin.refund.refund.StandardRefundType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundValidatorTest {

  private static Refund refund(Money amount) {
    return new Refund(
        RefundId.generate(),
        RefundNumber.of("RF-1"),
        amount,
        StandardRefundType.PARTIAL,
        RefundDirection.OUTBOUND,
        Reference.payment("PAY-1"));
  }

  @Test
  void wellFormedRefundWithinBalancePassesValidation() {
    ValidationResult result =
        RefundValidators.standard(
                RefundReasonRegistries.withProvider(RefundReasonRegistries.standardCatalog()))
            .validate(
                refund(Money.of("40.00", USD)),
                ValidationContext.at(Instant.now())
                    .withBalance(
                        new RefundBalance(
                            Money.of("100.00", USD), Money.zero(USD), Money.of("100.00", USD))));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void refundExceedingRemainingBalanceFailsValidation() {
    ValidationResult result =
        RefundValidators.standard(RefundReasonRegistries.empty())
            .validate(
                refund(Money.of("60.00", USD)),
                ValidationContext.at(Instant.now())
                    .withBalance(
                        new RefundBalance(
                            Money.of("100.00", USD),
                            Money.of("50.00", USD),
                            Money.of("50.00", USD))));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues())
        .anySatisfy(
            issue ->
                assertThat(issue.ruleCode()).isEqualTo("REFUND-REFUND_EXCEEDS_REFUNDABLE_BALANCE"));
  }

  @Test
  void currencyMismatchAgainstBalanceFailsValidation() {
    io.genfin.money.currency.Currency eur =
        io.genfin.money.currency.CurrencyFactory.newCurrency()
            .code("EUR")
            .symbol("e")
            .displayName("Euro")
            .fractionDigits(2)
            .build();

    ValidationResult result =
        RefundValidators.standard(RefundReasonRegistries.empty())
            .validate(
                refund(Money.of("10.00", eur)),
                ValidationContext.at(Instant.now())
                    .withBalance(
                        new RefundBalance(
                            Money.of("100.00", USD), Money.zero(USD), Money.of("100.00", USD))));

    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("REFUND-CURRENCY_MISMATCH"));
  }

  @Test
  void duplicateReferenceInContextFailsValidation() {
    ValidationResult result =
        RefundValidators.standard(RefundReasonRegistries.empty())
            .validate(
                refund(Money.of("10.00", USD)),
                ValidationContext.at(Instant.now()).withDuplicateReference(true));

    assertThat(result.issues())
        .anySatisfy(
            issue -> assertThat(issue.ruleCode()).isEqualTo("REFUND-DUPLICATE_REFUND_REFERENCE"));
  }

  @Test
  void unpermittedReasonInContextFailsValidation() {
    ValidationResult result =
        RefundValidators.standard(RefundReasonRegistries.empty())
            .validate(
                refund(Money.of("10.00", USD)),
                ValidationContext.at(Instant.now()).withReason(StandardRefundReason.FRAUD));

    assertThat(result.issues())
        .anySatisfy(
            issue -> assertThat(issue.ruleCode()).isEqualTo("REFUND-INVALID_REFUND_REASON"));
  }

  @Test
  void refundPastWindowInContextFailsValidation() {
    ValidationResult result =
        RefundValidators.standard(RefundReasonRegistries.empty())
            .validate(
                refund(Money.of("10.00", USD)),
                ValidationContext.at(Instant.now())
                    .withPaymentDate(Instant.parse("2000-01-01T00:00:00Z")));

    assertThat(result.issues())
        .anySatisfy(
            issue -> assertThat(issue.ruleCode()).isEqualTo("REFUND-REFUND_WINDOW_EXPIRED"));
  }

  @Test
  void additionalRulesComposeWithDefaults() {
    ValidationRule alwaysFails =
        (r, ctx) ->
            List.of(
                ValidationIssue.of(
                    "CUSTOM_RULE", "always fails", io.genfin.api.exception.Severity.ERROR));

    ValidationResult result =
        RefundValidators.withRules(RefundReasonRegistries.empty(), List.of(alwaysFails))
            .validate(refund(Money.of("10.00", USD)), ValidationContext.at(Instant.now()));

    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("CUSTOM_RULE"));
  }
}
