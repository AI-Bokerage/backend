package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.InvoiceNumberGenerator;
import com.company.bsmsvc.domain.enums.InvoiceLineItemType;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.InvoiceLineItem;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.InvoiceEventPublisher;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Verifies the recurring-invoice uniqueness invariant enforced by InvoiceGenerationServiceImpl.
 *
 * Invariant: for a given (subscriptionId, periodStart, periodEnd), exactly ONE recurring invoice
 * (source MANUAL or SUBSCRIPTION_RENEWAL) may ever exist. Non-recurring sources (UPGRADE,
 * DOWNGRADE, ADJUSTMENT) are exempt from this constraint.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceGenerationServiceImplTest {

    @Mock private PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort;
    @Mock private InvoiceNumberGenerator invoiceNumberGenerator;
    @Mock private InvoiceEventPublisher invoiceEventPublisher;
    @Mock private com.company.bsmsvc.domain.port.EventPublisherPort auditEventPublisher;
    @InjectMocks private InvoiceGenerationServiceImpl service;

    private UUID tenantId;
    private UUID subscriptionId;
    private Instant periodStart;
    private Instant periodEnd;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        periodStart = Instant.parse("2026-06-01T00:00:00Z");
        periodEnd   = Instant.parse("2026-07-01T00:00:00Z");

        when(invoiceNumberGenerator.generateInvoiceNumber(any(), any())).thenReturn("INV-001");
        when(platformInvoiceRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // default: no recurring invoice exists
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            any(), any(), any())).thenReturn(false);
    }

    // ── Test 1: MANUAL recurring invoice rejected when one already exists ─────

    @Test
    void generateInvoice_manualSource_existingRecurring_throwsBusinessRuleViolation() {
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            subscriptionId, periodStart, periodEnd)).thenReturn(true);

        assertThatThrownBy(() -> service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.MANUAL))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("recurring invoice already exists");

        verify(platformInvoiceRepositoryPort, never()).save(any());
    }

    // ── Test 2: Renewal invoice created first → second manual recurring rejected ──

    @Test
    void generateInvoice_renewalCreatedFirst_manualRejected() {
        // A SUBSCRIPTION_RENEWAL invoice already exists for this billing period
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            subscriptionId, periodStart, periodEnd)).thenReturn(true);

        assertThatThrownBy(() -> service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.MANUAL))
            .isInstanceOf(BusinessRuleViolationException.class);

        verify(platformInvoiceRepositoryPort, never()).save(any());
    }

    // ── Test 3: UPGRADE invoice is allowed alongside an existing recurring invoice ─

    @Test
    void generateInvoice_upgradeSource_allowedAlongsideRecurring() {
        // Recurring check must NOT be called for UPGRADE source; even if it were, it must not block
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            any(), any(), any())).thenReturn(true); // simulate existing — should be ignored

        PlatformInvoice result = service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.UPGRADE);

        assertThat(result).isNotNull();
        verify(platformInvoiceRepositoryPort).save(any());
    }

    // ── Test 4: DOWNGRADE invoice is allowed alongside an existing recurring invoice ─

    @Test
    void generateInvoice_downgradeSource_allowedAlongsideRecurring() {
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            any(), any(), any())).thenReturn(true);

        PlatformInvoice result = service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.DOWNGRADE);

        assertThat(result).isNotNull();
        verify(platformInvoiceRepositoryPort).save(any());
    }

    // ── Test 5: ADJUSTMENT invoice is allowed alongside an existing recurring invoice ─

    @Test
    void generateInvoice_adjustmentSource_allowedAlongsideRecurring() {
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            any(), any(), any())).thenReturn(true);

        PlatformInvoice result = service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.ADJUSTMENT);

        assertThat(result).isNotNull();
        verify(platformInvoiceRepositoryPort).save(any());
    }

    // ── Test 6: MANUAL creates successfully when no existing recurring invoice ─

    @Test
    void generateInvoice_manualSource_noExisting_createsSuccessfully() {
        PlatformInvoice result = service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.MANUAL);

        assertThat(result).isNotNull();
        assertThat(result.getSource()).isEqualTo(InvoiceSource.MANUAL);
        verify(platformInvoiceRepositoryPort).save(any());
        // NEW audit-only leg: bsm.invoice.created enqueued via the transactional outbox,
        // additive to (never replacing) the existing publishAfterCommit business publish below.
        verify(auditEventPublisher).publish(
            org.mockito.ArgumentMatchers.eq("bsm.invoice.created"),
            org.mockito.ArgumentMatchers.eq(tenantId),
            org.mockito.ArgumentMatchers.eq("Invoice"),
            org.mockito.ArgumentMatchers.eq(result.getId()),
            org.mockito.Mockito.isNull(),
            any());
        verify(invoiceEventPublisher).publishInvoiceCreated(any());
    }

    // ── Test 7: SUBSCRIPTION_RENEWAL rejects second recurring via soft check ──

    @Test
    void generateInvoice_renewalSource_existingRecurring_throwsBusinessRuleViolation() {
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            subscriptionId, periodStart, periodEnd)).thenReturn(true);

        assertThatThrownBy(() -> service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.SUBSCRIPTION_RENEWAL))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("recurring invoice already exists");

        verify(platformInvoiceRepositoryPort, never()).save(any());
    }

    // ── Test 8: Concurrent creation — DB constraint wraps to BusinessRuleViolationException ─

    @Test
    void generateInvoice_concurrentRecurring_dbConstraintWrappedAsBusinessRuleViolation() {
        // Soft check passes (race), but DB partial unique index rejects the second insert
        when(platformInvoiceRepositoryPort.save(any()))
            .thenThrow(new DataIntegrityViolationException("unique constraint violation"));

        assertThatThrownBy(() -> service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.MANUAL))
            .isInstanceOf(BusinessRuleViolationException.class)
            .hasMessageContaining("recurring invoice already exists");
    }

    // ── Test 9: Non-recurring DataIntegrityViolationException is not swallowed ─

    @Test
    void generateInvoice_nonRecurring_dbConstraintRethrown() {
        when(platformInvoiceRepositoryPort.save(any()))
            .thenThrow(new DataIntegrityViolationException("some other constraint violation"));

        assertThatThrownBy(() -> service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), InvoiceSource.UPGRADE))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Test 10: null source defaults to MANUAL (recurring) ──────────────────

    @Test
    void generateInvoice_nullSource_defaultsToManual_existingRecurring_throws() {
        when(platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
            subscriptionId, periodStart, periodEnd)).thenReturn(true);

        assertThatThrownBy(() -> service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(), null))
            .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ── Test 11: Line items are assigned invoiceId ────────────────────────────

    @Test
    void generateInvoice_lineItemsWithoutInvoiceId_arePopulated() {
        InvoiceLineItem item = InvoiceLineItem.builder()
            .itemType(InvoiceLineItemType.SUBSCRIPTION)
            .description("Sub fee").quantity(1).unitAmountMinor(9900L).amountMinor(9900L)
            .createdAt(Instant.now()).build();

        PlatformInvoice result = service.generateInvoice(
            tenantId, subscriptionId, "INR", periodStart, periodEnd,
            LocalDate.now().plusDays(7), List.of(item), InvoiceSource.MANUAL);

        assertThat(result).isNotNull();
    }
}
