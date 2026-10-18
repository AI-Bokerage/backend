package io.genfin.payment.validation;

import static io.genfin.payment.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;

import io.genfin.api.time.ClockProviders;
import io.genfin.money.money.Money;
import io.genfin.payment.idempotency.IdempotencyKey;
import io.genfin.payment.idempotency.IdempotencyOutcome;
import io.genfin.payment.idempotency.IdempotencyResult;
import io.genfin.payment.method.MethodBuilder;
import io.genfin.payment.method.PaymentMethodRegistries;
import io.genfin.payment.method.PaymentMethodType;
import io.genfin.payment.method.StandardPaymentMethodType;
import io.genfin.payment.payment.Payment;
import io.genfin.payment.payment.PaymentBuilder;
import io.genfin.payment.port.method.PaymentMethodRegistry;
import io.genfin.payment.reference.Reference;
import io.genfin.payment.session.SessionBuilder;
import io.genfin.payment.session.SessionReference;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentValidatorTest {

  private static Payment payment(PaymentMethodType methodType) {
    return PaymentBuilder.newPayment()
        .amount(Money.of("10.00", USD))
        .method(MethodBuilder.newMethod().type(methodType).maskedIdentifier("x").build())
        .clockProvider(ClockProviders.system())
        .actor("t")
        .build();
  }

  @Test
  void wellFormedPaymentWithKnownMethodPassesValidation() {
    ValidationResult result =
        PaymentValidators.standard()
            .validate(payment(StandardPaymentMethodType.CARD), ValidationContext.at(Instant.now()));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void unknownPaymentMethodFailsValidation() {
    PaymentMethodType unknown = () -> "NOT_REGISTERED";

    ValidationResult result =
        PaymentValidators.standard()
            .validate(payment(unknown), ValidationContext.at(Instant.now()));

    assertThat(result.isValid()).isFalse();
    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("UNSUPPORTED_PAYMENT_METHOD"));
  }

  @Test
  void expiredSessionInContextFailsValidation() {
    var session =
        SessionBuilder.newSession()
            .amount(Money.of("10.00", USD))
            .expiresAt(Instant.parse("2020-01-01T00:00:00Z"))
            .reference(new SessionReference(Reference.invoice("INV-1")))
            .build();

    ValidationResult result =
        PaymentValidators.standard()
            .validate(
                payment(StandardPaymentMethodType.CARD),
                ValidationContext.at(Instant.now()).withSession(session));

    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("EXPIRED_SESSION"));
  }

  @Test
  void conflictingIdempotencyResultFailsValidation() {
    var conflict = new IdempotencyResult(IdempotencyKey.of("k"), IdempotencyOutcome.CONFLICT);

    ValidationResult result =
        PaymentValidators.standard()
            .validate(
                payment(StandardPaymentMethodType.CARD),
                ValidationContext.at(Instant.now()).withIdempotencyResult(conflict));

    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("DUPLICATE_IDEMPOTENCY_KEY"));
  }

  @Test
  void additionalRulesComposeWithDefaults() {
    PaymentMethodRegistry registry =
        PaymentMethodRegistries.withProvider(PaymentMethodRegistries.standardCatalog());
    ValidationRule alwaysFails =
        (p, ctx) ->
            java.util.List.of(
                ValidationIssue.of(
                    "CUSTOM_RULE", "always fails", io.genfin.api.exception.Severity.ERROR));

    ValidationResult result =
        PaymentValidators.withRules(registry, java.util.List.of(alwaysFails))
            .validate(payment(StandardPaymentMethodType.CARD), ValidationContext.at(Instant.now()));

    assertThat(result.issues())
        .anySatisfy(issue -> assertThat(issue.ruleCode()).isEqualTo("CUSTOM_RULE"));
  }
}
