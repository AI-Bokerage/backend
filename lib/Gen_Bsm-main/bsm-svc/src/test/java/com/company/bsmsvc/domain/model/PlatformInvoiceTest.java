package com.company.bsmsvc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.event.InvoiceMarkedPaidEvent;
import com.company.bsmsvc.domain.event.InvoiceVoidedEvent;
import com.company.bsmsvc.domain.event.LineItemAddedEvent;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformInvoiceTest {

    private PlatformInvoice invoice;
    private UUID invoiceId;
    private UUID tenantId;
    private UUID subscriptionId;

    @BeforeEach
    void setUp() {
        invoiceId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        invoice = PlatformInvoice.builder()
            .id(invoiceId)
            .tenantId(tenantId)
            .subscriptionId(subscriptionId)
            .invoiceNumber("INV-2026-001")
            .status(InvoiceStatus.DRAFT)
            .currency("INR")
            .periodStart(Instant.now())
            .periodEnd(Instant.now().plusSeconds(86400))
            .dueDate(LocalDate.now().plusDays(15))
            .lineItems(new ArrayList<>())
            .domainEvents(new ArrayList<>())
            .build();
    }

    @Test
    void testOpenInvoiceSuccessfulTransition() {
        invoice.openInvoice();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice.isOpen()).isTrue();
        assertThat(invoice.isPaid()).isFalse();
    }

    @Test
    void testOpenInvoiceInvalidTransition() {
        invoice = invoice.toBuilder().status(InvoiceStatus.PAID).build();
        assertThatThrownBy(() -> invoice.openInvoice())
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Transition to OPEN is not allowed from status: PAID");
    }

    @Test
    void testMarkPaidFromOpenSuccessfulTransition() {
        invoice.openInvoice();
        Instant paidTime = Instant.now();
        invoice.markPaid(paidTime);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.isPaid()).isTrue();
        assertThat(invoice.getPaidAt()).isEqualTo(paidTime);
        assertThat(invoice.getAmountPaid()).isEqualTo(invoice.getAmountDue());

        assertThat(invoice.pullDomainEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(InvoiceMarkedPaidEvent.class);
    }

    @Test
    void testMarkPaidFromPartiallyPaidSuccessfulTransition() {
        invoice.openInvoice();
        invoice.markPartiallyPaid(100L, Instant.now());
        invoice.markPaid(Instant.now());

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.isPaid()).isTrue();
    }

    @Test
    void testMarkPaidIdempotent() {
        invoice = invoice.toBuilder().status(InvoiceStatus.PAID).build();
        invoice.markPaid(Instant.now());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void testMarkPaidInvalidTransition() {
        assertThatThrownBy(() -> invoice.markPaid(Instant.now()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Transition to PAID is not allowed from status: DRAFT");
    }

    @Test
    void testMarkPartiallyPaidSuccessfulTransition() {
        invoice.openInvoice();
        invoice.addLineItem(InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(invoiceId)
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Service Subscription")
            .quantity(1)
            .unitAmountMinor(500L)
            .amountMinor(500L)
            .build());

        Instant now = Instant.now();
        invoice.markPartiallyPaid(200L, now);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(invoice.getAmountPaid()).isEqualTo(200L);
    }

    @Test
    void testMarkPartiallyPaidThresholdTransition() {
        invoice.openInvoice();
        invoice.addLineItem(InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(invoiceId)
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Service Subscription")
            .quantity(1)
            .unitAmountMinor(500L)
            .amountMinor(500L)
            .build());

        Instant now = Instant.now();
        invoice.markPartiallyPaid(500L, now);

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getAmountPaid()).isEqualTo(500L);
        assertThat(invoice.getPaidAt()).isEqualTo(now);
    }

    @Test
    void testMarkPartiallyPaidInvalidTransition() {
        assertThatThrownBy(() -> invoice.markPartiallyPaid(100L, Instant.now()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Transition to PARTIALLY_PAID is not allowed from status: DRAFT");
    }

    @Test
    void testMarkPartiallyPaidNegativeAmount() {
        invoice.openInvoice();
        assertThatThrownBy(() -> invoice.markPartiallyPaid(-10L, Instant.now()))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Amount paid cannot be negative: -10");
    }

    @Test
    void testVoidInvoiceSuccessfulTransition() {
        invoice.openInvoice();
        invoice.voidInvoice();

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VOID);
        assertThat(invoice.pullDomainEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(InvoiceVoidedEvent.class);
    }

    @Test
    void testVoidInvoiceInvalidTransition() {
        assertThatThrownBy(() -> invoice.voidInvoice())
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Transition to VOID is not allowed from status: DRAFT");
    }

    @Test
    void testRefundInvoiceSuccessfulTransition() {
        invoice = invoice.toBuilder().status(InvoiceStatus.PAID).build();
        invoice.refund();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.REFUNDED);
    }

    @Test
    void testRefundInvoiceInvalidTransition() {
        assertThatThrownBy(() -> invoice.refund())
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Transition to REFUNDED is not allowed from status: DRAFT");
    }

    @Test
    void testAddLineItemSuccessful() {
        UUID itemId = UUID.randomUUID();
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(itemId)
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Monthly Subscription Charge")
            .quantity(2)
            .unitAmountMinor(250L)
            .amountMinor(500L)
            .build();

        invoice.addLineItem(item);

        assertThat(invoice.getLineItems()).hasSize(1);
        assertThat(invoice.getAmountDue()).isEqualTo(500L);
        assertThat(invoice.getLineItems().get(0).getInvoiceId()).isEqualTo(invoiceId);

        // Clear events and verify events
        assertThat(invoice.pullDomainEvents())
            .hasSize(1)
            .first()
            .isInstanceOf(LineItemAddedEvent.class);
    }

    @Test
    void testAddLineItemNullThrows() {
        assertThatThrownBy(() -> invoice.addLineItem(null))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Line item cannot be null");
    }

    @Test
    void testAddLineItemMismatchInvoiceIdThrows() {
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(UUID.randomUUID())
            .invoiceId(UUID.randomUUID()) // different ID
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Charge")
            .quantity(1)
            .unitAmountMinor(100L)
            .amountMinor(100L)
            .build();

        assertThatThrownBy(() -> invoice.addLineItem(item))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("Line item belongs to a different invoice");
    }

    @Test
    void testRemoveLineItem() {
        UUID itemId = UUID.randomUUID();
        InvoiceLineItem item = InvoiceLineItem.builder()
            .id(itemId)
            .invoiceId(invoiceId)
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Charge")
            .quantity(1)
            .unitAmountMinor(100L)
            .amountMinor(100L)
            .build();

        invoice.addLineItem(item);
        assertThat(invoice.getAmountDue()).isEqualTo(100L);

        invoice.removeLineItem(itemId);
        assertThat(invoice.getLineItems()).isEmpty();
        assertThat(invoice.getAmountDue()).isEqualTo(0L);
    }

    @Test
    void testDomainEventsMethods() {
        invoice.registerEvent("TEST_EVENT");
        assertThat(invoice.pullDomainEvents()).containsExactly("TEST_EVENT");
        assertThat(invoice.pullDomainEvents()).isEmpty();

        invoice.registerEvent("ANOTHER_EVENT");
        invoice.clearDomainEvents();
        assertThat(invoice.pullDomainEvents()).isEmpty();
    }
}
