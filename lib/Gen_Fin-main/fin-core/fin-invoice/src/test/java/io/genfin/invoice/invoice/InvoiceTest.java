package io.genfin.invoice.invoice;

import static io.genfin.invoice.support.TestCurrencies.USD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.genfin.api.event.DomainEvent;
import io.genfin.api.port.time.ClockProvider;
import io.genfin.api.time.ClockProviders;
import io.genfin.invoice.event.InvoiceCancelled;
import io.genfin.invoice.event.InvoiceCreated;
import io.genfin.invoice.event.InvoiceIssued;
import io.genfin.invoice.event.InvoicePaid;
import io.genfin.invoice.event.InvoiceUpdated;
import io.genfin.invoice.exception.IllegalInvoiceStateTransitionException;
import io.genfin.invoice.factory.LineFactory;
import io.genfin.invoice.lifecycle.StandardInvoiceState;
import io.genfin.invoice.numbering.InvoiceNumber;
import io.genfin.money.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoiceTest {

  private static final ClockProvider CLOCK =
      ClockProviders.fixed(Instant.parse("2026-01-01T00:00:00Z"));

  private static Invoice draftInvoice() {
    return InvoiceBuilder.newInvoice()
        .currency(USD)
        .dueDate(Instant.parse("2026-02-01T00:00:00Z"))
        .clockProvider(CLOCK)
        .actor("tester")
        .build();
  }

  @Test
  void newInvoiceStartsInDraftAndRecordsCreatedEvent() {
    Invoice invoice = draftInvoice();

    assertThat(invoice.status()).isEqualTo(StandardInvoiceState.DRAFT);
    List<DomainEvent> events = invoice.pullEvents();
    assertThat(events).hasSize(1).first().isInstanceOf(InvoiceCreated.class);
  }

  @Test
  void linesCanOnlyBeAddedWhileDraft() {
    Invoice invoice = draftInvoice();
    invoice.addLine(
        LineFactory.simple("Widget", BigDecimal.ONE, Money.of("10.00", USD)), CLOCK, "tester");
    invoice.issue(InvoiceNumber.of("INV-1"), CLOCK, "tester");

    assertThatThrownBy(
            () ->
                invoice.addLine(
                    LineFactory.simple("Late", BigDecimal.ONE, Money.of("5.00", USD)),
                    CLOCK,
                    "tester"))
        .isInstanceOf(IllegalInvoiceStateTransitionException.class);
  }

  @Test
  void issueSetsNumberAndFiresIssuedEvent() {
    Invoice invoice = draftInvoice();
    invoice.pullEvents();

    invoice.issue(InvoiceNumber.of("INV-1"), CLOCK, "tester");

    assertThat(invoice.number()).contains(InvoiceNumber.of("INV-1"));
    assertThat(invoice.status()).isEqualTo(StandardInvoiceState.ISSUED);
    assertThat(invoice.pullEvents()).anyMatch(InvoiceIssued.class::isInstance);
  }

  @Test
  void sendingBeforeIssuingIsRejected() {
    Invoice invoice = draftInvoice();

    assertThatThrownBy(() -> invoice.send(CLOCK, "tester"))
        .isInstanceOf(IllegalInvoiceStateTransitionException.class);
  }

  @Test
  void fullPaymentTransitionsToPaidAndFiresInvoicePaid() {
    Invoice invoice = draftInvoice();
    invoice.issue(InvoiceNumber.of("INV-1"), CLOCK, "tester");
    invoice.pullEvents();

    invoice.recordPayment(Money.of("100.00", USD), Money.of("100.00", USD), CLOCK, "tester");

    assertThat(invoice.status()).isEqualTo(StandardInvoiceState.PAID);
    assertThat(invoice.amountPaid()).isEqualTo(Money.of("100.00", USD));
    assertThat(invoice.pullEvents()).anyMatch(InvoicePaid.class::isInstance);
  }

  @Test
  void partialPaymentTransitionsToPartiallyPaidAndFiresUpdated() {
    Invoice invoice = draftInvoice();
    invoice.issue(InvoiceNumber.of("INV-1"), CLOCK, "tester");
    invoice.pullEvents();

    invoice.recordPayment(Money.of("40.00", USD), Money.of("100.00", USD), CLOCK, "tester");

    assertThat(invoice.status()).isEqualTo(StandardInvoiceState.PARTIALLY_PAID);
    assertThat(invoice.pullEvents()).anyMatch(InvoiceUpdated.class::isInstance);
  }

  @Test
  void cancelFiresCancelledEventWithReason() {
    Invoice invoice = draftInvoice();
    invoice.pullEvents();

    invoice.cancel("customer request", CLOCK, "tester");

    assertThat(invoice.status()).isEqualTo(StandardInvoiceState.CANCELLED);
    assertThat(invoice.pullEvents())
        .filteredOn(InvoiceCancelled.class::isInstance)
        .first()
        .satisfies(
            event -> assertThat(((InvoiceCancelled) event).reason()).isEqualTo("customer request"));
  }

  @Test
  void closeRequiresTerminalState() {
    Invoice invoice = draftInvoice();

    assertThatThrownBy(() -> invoice.close(CLOCK, "tester"))
        .isInstanceOf(IllegalInvoiceStateTransitionException.class);

    invoice.cancel("test", CLOCK, "tester");
    invoice.close(CLOCK, "tester");

    assertThat(invoice.closed()).isTrue();
  }

  @Test
  void versionIncrementsOnEveryMutation() {
    Invoice invoice = draftInvoice();
    int initialVersion = invoice.version();

    invoice.addTag("urgent", CLOCK, "tester");

    assertThat(invoice.version()).isEqualTo(initialVersion + 1);
  }

  @Test
  void equalityIsByIdentityRegardlessOfState() {
    Invoice invoice = draftInvoice();
    Invoice other = draftInvoice();

    assertThat(invoice).isNotEqualTo(other);
    assertThat(invoice).isEqualTo(invoice);
  }
}
