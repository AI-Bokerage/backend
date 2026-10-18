package io.genfin.refund.refund;

import static io.genfin.refund.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.time.ClockProviders;
import io.genfin.money.money.Money;
import io.genfin.refund.calculation.RefundBalance;
import io.genfin.refund.calculation.RefundCalculators;
import io.genfin.refund.event.RefundApproved;
import io.genfin.refund.event.RefundCancelled;
import io.genfin.refund.event.RefundCompleted;
import io.genfin.refund.event.RefundRequested;
import io.genfin.refund.event.RefundReversed;
import io.genfin.refund.exception.IllegalRefundStateTransitionException;
import io.genfin.refund.lifecycle.StandardRefundStatus;
import io.genfin.refund.reference.Reference;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefundTest {

  private static final ClockProvider CLOCK =
      ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z"));

  private static Refund newRefund(Money amount) {
    return RefundBuilder.newRefund()
        .refundNumber(RefundNumber.of("RFD-1"))
        .amount(amount)
        .paymentReference(Reference.payment("payment-1"))
        .build();
  }

  @Test
  void newRefundStartsRequestedWithOnlyThePaymentReference() {
    Refund refund = newRefund(Money.of("40.00", USD));

    assertThat(refund.status()).isEqualTo(StandardRefundStatus.REQUESTED);
    assertThat(refund.references().all()).hasSize(1);
  }

  @Test
  void aZeroOrNegativeAmountIsRejectedAtConstruction() {
    assertThatThrownBy(() -> newRefund(Money.zero(USD)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aNonPaymentReferenceIsRejectedAsThePrimaryReference() {
    assertThatThrownBy(
            () ->
                RefundBuilder.newRefund()
                    .refundNumber(RefundNumber.of("RFD-1"))
                    .amount(Money.of("10.00", USD))
                    .paymentReference(Reference.invoice("invoice-1"))
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fullRefundSubmitApproveProcessCompleteRecordsExpectedEvents() {
    Refund refund = newRefund(Money.of("100.00", USD));

    refund.submitForApproval(CLOCK);
    refund.approve(CLOCK);
    refund.startProcessing(CLOCK);
    refund.completeProcessing(CLOCK);

    assertThat(refund.status()).isEqualTo(StandardRefundStatus.COMPLETED);
    assertThat(refund.pullEvents())
        .hasSize(4)
        .satisfiesExactly(
            e -> assertThat(e).isInstanceOf(RefundRequested.class),
            e -> assertThat(e).isInstanceOf(RefundApproved.class),
            e -> assertThat(e).isInstanceOf(io.genfin.refund.event.RefundStarted.class),
            e -> assertThat(e).isInstanceOf(RefundCompleted.class));
  }

  @Test
  void rejectedRefundCannotLaterBeApproved() {
    Refund refund = newRefund(Money.of("100.00", USD));
    refund.submitForApproval(CLOCK);
    refund.reject("not eligible", CLOCK);

    assertThat(refund.status()).isEqualTo(StandardRefundStatus.REJECTED);
    assertThatThrownBy(() -> refund.approve(CLOCK))
        .isInstanceOf(IllegalRefundStateTransitionException.class);
  }

  @Test
  void cancelIsAllowedBeforeProcessingButNotAfterCompletion() {
    Refund cancellable = newRefund(Money.of("100.00", USD));
    cancellable.submitForApproval(CLOCK);
    cancellable.approve(CLOCK);
    cancellable.cancel("customer withdrew request", CLOCK);
    assertThat(cancellable.status()).isEqualTo(StandardRefundStatus.CANCELLED);
    assertThat(cancellable.pullEvents()).anyMatch(RefundCancelled.class::isInstance);

    Refund completed = newRefund(Money.of("100.00", USD));
    completed.submitForApproval(CLOCK);
    completed.approve(CLOCK);
    completed.startProcessing(CLOCK);
    completed.completeProcessing(CLOCK);

    assertThatThrownBy(() -> completed.cancel("too late", CLOCK))
        .isInstanceOf(IllegalRefundStateTransitionException.class);
  }

  @Test
  void completedRefundCanBeDisputedThenReversedAndRecordsBothEvents() {
    Refund refund = newRefund(Money.of("100.00", USD));
    refund.submitForApproval(CLOCK);
    refund.approve(CLOCK);
    refund.startProcessing(CLOCK);
    refund.completeProcessing(CLOCK);
    refund.pullEvents();

    refund.dispute("cardholder disputed the refund", CLOCK);
    refund.reverse(CLOCK);

    assertThat(refund.status()).isEqualTo(StandardRefundStatus.REVERSED);
    assertThat(refund.pullEvents()).anyMatch(RefundReversed.class::isInstance);
  }

  @Test
  void multiplePartialRefundsAgainstOnePaymentEachCompleteIndependentlyAndSumTowardBalance() {
    Money paymentTotal = Money.of("100.00", USD);
    Reference paymentReference = Reference.payment("payment-1");

    Refund first =
        RefundBuilder.newRefund()
            .refundNumber(RefundNumber.of("RFD-1"))
            .amount(Money.of("30.00", USD))
            .type(StandardRefundType.PARTIAL)
            .paymentReference(paymentReference)
            .build();
    Refund second =
        RefundBuilder.newRefund()
            .refundNumber(RefundNumber.of("RFD-2"))
            .amount(Money.of("20.00", USD))
            .type(StandardRefundType.PARTIAL)
            .paymentReference(paymentReference)
            .build();

    first.submitForApproval(CLOCK);
    first.approve(CLOCK);
    first.startProcessing(CLOCK);
    first.completeProcessing(CLOCK);

    second.submitForApproval(CLOCK);
    second.approve(CLOCK);
    second.startProcessing(CLOCK);
    second.completeProcessing(CLOCK);

    assertThat(first.status()).isEqualTo(StandardRefundStatus.COMPLETED);
    assertThat(second.status()).isEqualTo(StandardRefundStatus.COMPLETED);

    RefundBalance balance =
        RefundCalculators.standard()
            .calculateBalance(paymentTotal, List.of(first.amount(), second.amount()));

    assertThat(balance.totalRefunded()).isEqualTo(Money.of("50.00", USD));
    assertThat(balance.refundableRemaining()).isEqualTo(Money.of("50.00", USD));
    assertThat(balance.isFullyRefunded()).isFalse();
  }

  @Test
  void addingAReferenceKeepsThePaymentReferenceAndAppendsTheNewOne() {
    Refund refund = newRefund(Money.of("50.00", USD));

    refund.addReference(Reference.customer("customer-1"));

    assertThat(refund.references().all()).hasSize(2);
    assertThat(
            refund.references().byType(io.genfin.refund.reference.StandardReferenceType.CUSTOMER))
        .hasSize(1);
  }
}
