package io.genfin.refund.request;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.money.money.Money;
import io.genfin.refund.exception.IllegalRefundStateTransitionException;
import io.genfin.refund.id.RefundRequestId;
import io.genfin.refund.reason.RefundReasonRegistries;
import io.genfin.refund.reason.StandardRefundReason;
import io.genfin.refund.reference.Reference;
import io.genfin.refund.validation.RefundRequestValidators;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefundRequestTest {

  private static RefundRequest newRequest() {
    return new RefundRequest(
        RefundRequestId.generate(),
        Money.of("10.00", USD),
        StandardRefundReason.CUSTOMER_REQUEST,
        Reference.payment("payment-1"),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void startsPendingAndApprovingThenFulfillingReachesFulfilled() {
    RefundRequest request = newRequest();

    assertThat(request.status()).isEqualTo(RefundRequestStatus.PENDING);

    request.approve();
    assertThat(request.status()).isEqualTo(RefundRequestStatus.APPROVED);

    request.fulfill();
    assertThat(request.status()).isEqualTo(RefundRequestStatus.FULFILLED);
  }

  @Test
  void cannotApproveTwice() {
    RefundRequest request = newRequest();
    request.approve();

    assertThatThrownBy(request::approve).isInstanceOf(IllegalRefundStateTransitionException.class);
  }

  @Test
  void cancelIsAllowedFromPendingOrApprovedButNotAfterFulfillment() {
    RefundRequest request = newRequest();
    request.approve();
    request.fulfill();

    assertThatThrownBy(request::cancel).isInstanceOf(IllegalRefundStateTransitionException.class);
  }

  @Test
  void validatorRejectsAReasonNotInTheRegistry() {
    RefundRequest request = newRequest();
    var emptyRegistry = RefundReasonRegistries.empty();

    var violations = RefundRequestValidators.standard(emptyRegistry).validate(request);

    assertThat(violations).isNotEmpty();
  }

  @Test
  void validatorAcceptsARequestWithAKnownReasonAndAPaymentReference() {
    RefundRequest request = newRequest();
    var registry = RefundReasonRegistries.withProvider(RefundReasonRegistries.standardCatalog());

    var violations = RefundRequestValidators.standard(registry).validate(request);

    assertThat(violations).isEmpty();
  }
}
