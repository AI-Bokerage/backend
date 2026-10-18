package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.CreditNoteService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.domain.port.TenantScopePort;
import com.company.bsmsvc.application.service.RefundService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.enums.RefundStatus;
import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.exception.InvoiceNotFoundException;
import com.company.bsmsvc.domain.exception.PaymentGatewayException;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.model.CreditNoteFilter;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.RefundRequest;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.payment.RefundCommand;
import com.company.bsmsvc.domain.port.PaymentGatewayPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.RefundRequestRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Reliable refund service.
 *
 * Reliability model:
 *  1. RefundRequest(PENDING) committed before provider call → provider refund ID is never lost.
 *  2. Provider call runs outside any DB transaction.
 *  3. After provider succeeds: RefundRequest(PROVIDER_REFUND_SUCCEEDED) committed immediately.
 *  4. Local accounting (credit note, ledger, payment update) runs next.
 *  5. Any failure in step 4 → RefundRequest(RECOVERY_REQUIRED); scheduler retries automatically.
 *
 * Duplicate prevention:
 *  An active RefundRequest (non-FAILED) for the same (invoiceId, paymentId, amount) blocks
 *  a second provider call. This prevents double refunds on retry.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final PlatformInvoiceRepositoryPort invoiceRepository;
    private final PaymentRepositoryPort paymentRepository;
    private final TenantBillingProfileService billingProfileService;
    private final PaymentGatewayResolver resolver;
    private final CreditNoteService creditNoteService;
    private final RefundRequestRepositoryPort refundRequestRepository;
    private final InvoiceService invoiceService;
    private final TenantScopePort tenantScopeEnforcer;
    private final EventPublisherPort auditEventPublisher;

    @Override
    // NO @Transactional — each step commits independently so the provider refund ID
    // is never lost even if local accounting fails.
    public CreditNote createRefund(UUID tenantId, UUID invoiceId, long amountMinor, String reason, UUID requestedBy) {
        tenantScopeEnforcer.assertTenantAccess(tenantId);
        PlatformInvoice invoice = loadAndValidateInvoice(invoiceId, tenantId, amountMinor);
        Payment succeededPayment = findSucceededPayment(invoiceId);

        UUID paymentId = succeededPayment != null ? succeededPayment.getId() : null;

        // ── Idempotency: check for an existing active RefundRequest ──────────────
        Optional<RefundRequest> existingOpt = paymentId != null
            ? refundRequestRepository.findActiveByInvoiceAndPaymentAndAmount(invoiceId, paymentId, amountMinor)
            : Optional.empty();

        if (existingOpt.isPresent()) {
            return handleExistingRequest(existingOpt.get(), invoice, succeededPayment, reason, requestedBy);
        }

        // ── Step 1: Persist RefundRequest(PENDING) — commits now ────────────────
        TenantBillingProfile profile = billingProfileService.getProfile(tenantId);
        RefundRequest refundRequest;
        try {
            refundRequest = refundRequestRepository.save(RefundRequest.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .invoiceId(invoiceId)
                .paymentId(paymentId)
                .requestedAmountMinor(amountMinor)
                .provider(profile.getPaymentProvider())
                .status(RefundStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        } catch (DataIntegrityViolationException ex) {
            // Concurrent request already committed a PENDING record for the same
            // (invoice, payment, amount) — the DB unique index caught the race.
            throw new BusinessRuleViolationException(
                "A refund for this payment is already in progress (concurrent request detected).");
        }

        log.info("RefundRequest created refundRequestId={} invoiceId={} amount={}",
            refundRequest.getId(), invoiceId, amountMinor);
        auditEventPublisher.publish("refund.created", tenantId, "RefundRequest", refundRequest.getId(),
            requestedBy, refundAuditData(refundRequest, reason));

        // ── Step 2: Call provider — outside any transaction ─────────────────────
        String providerRefundId;
        try {
            PaymentGatewayPort gateway = resolver.resolve(profile.getPaymentProvider());
            String chargeRef = succeededPayment != null ? succeededPayment.getExternalChargeId() : null;
            if (chargeRef == null) {
                throw new BusinessRuleViolationException("No provider charge reference found for payment");
            }
            var result = gateway.refund(new RefundCommand(chargeRef, amountMinor, reason));
            providerRefundId = result.externalRefundId();
        } catch (Exception e) {
            refundRequestRepository.save(refundRequest.toBuilder()
                .status(RefundStatus.FAILED)
                .failureReason(e.getMessage())
                .updatedAt(Instant.now())
                .build());
            auditEventPublisher.publish("refund.failed", tenantId, "RefundRequest", refundRequest.getId(),
                requestedBy, refundAuditData(refundRequest, e.getMessage()));
            log.error("RefundRequest FAILED at provider refundRequestId={}: {}", refundRequest.getId(), e.getMessage());
            throw e instanceof PaymentGatewayException
                ? (PaymentGatewayException) e
                : new PaymentGatewayException("Provider refund failed: " + e.getMessage(), e);
        }

        // ── Step 3: Persist providerRefundId — commits now ──────────────────────
        UUID savedRefundRequestId = refundRequest.getId();
        refundRequestRepository.save(refundRequest.toBuilder()
            .providerRefundId(providerRefundId)
            .status(RefundStatus.PROVIDER_REFUND_SUCCEEDED)
            .updatedAt(Instant.now())
            .build());

        // Reload after commit so we have the DB-incremented version.
        // The save() return value captures the entity before the @Transactional proxy
        // commits the transaction — meaning the @Version increment hasn't happened yet.
        // Using a stale version in the next save causes an OptimisticLockException.
        refundRequest = refundRequestRepository.findById(savedRefundRequestId)
            .orElseThrow(() -> new BusinessRuleViolationException(
                "RefundRequest disappeared after PROVIDER_REFUND_SUCCEEDED save: " + savedRefundRequestId));

        log.info("RefundRequest PROVIDER_REFUND_SUCCEEDED refundRequestId={} providerRefundId={}",
            refundRequest.getId(), providerRefundId);

        // ── Step 4: Local accounting — if this fails, RECOVERY_REQUIRED is set ──
        return completeLocalWork(refundRequest, invoice, succeededPayment, reason, requestedBy);
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private CreditNote handleExistingRequest(RefundRequest existing, PlatformInvoice invoice,
                                              Payment succeededPayment, String reason, UUID requestedBy) {
        log.info("RefundRequest exists refundRequestId={} status={}", existing.getId(), existing.getStatus());
        return switch (existing.getStatus()) {
            case COMPLETED -> {
                // Return the existing credit note — no duplicate provider call
                CreditNote cn = existing.getCreditNoteId() != null
                    ? creditNoteService.getCreditNoteById(existing.getCreditNoteId())
                    : null;
                if (cn == null) throw new BusinessRuleViolationException(
                    "Refund already COMPLETED but credit note not found. refundRequestId=" + existing.getId());
                log.info("RefundRequest already COMPLETED, returning existing credit note creditNoteId={}", cn.getId());
                yield cn;
            }
            case RECOVERY_REQUIRED, PROVIDER_REFUND_SUCCEEDED ->
                // Provider already processed — just complete local work
                completeLocalWork(existing, invoice, succeededPayment, reason, requestedBy);
            case PENDING ->
                throw new BusinessRuleViolationException(
                    "A refund for this payment is already in progress. refundRequestId=" + existing.getId());
            default ->
                throw new BusinessRuleViolationException(
                    "Unexpected refund request state: " + existing.getStatus());
        };
    }

    private CreditNote completeLocalWork(RefundRequest refundRequest, PlatformInvoice invoice,
                                          Payment succeededPayment, String reason, UUID requestedBy) {
        UUID refundRequestId = refundRequest.getId();
        // Skip if credit note was already created (idempotent on re-entry from recovery)
        CreditNote creditNote;
        if (refundRequest.getCreditNoteId() != null) {
            creditNote = creditNoteService.getCreditNoteById(refundRequest.getCreditNoteId());
            log.info("RefundRequest local work re-entry — credit note exists creditNoteId={}", creditNote.getId());
        } else {
            try {
                creditNote = creditNoteService.createCreditNote(
                    refundRequest.getTenantId(), refundRequest.getInvoiceId(),
                    refundRequest.getRequestedAmountMinor(), invoice.getCurrency(),
                    reason, requestedBy);
                // Persist creditNoteId immediately so recovery finds it on re-run
                refundRequestRepository.save(refundRequest.toBuilder()
                    .creditNoteId(creditNote.getId())
                    .updatedAt(Instant.now())
                    .build());
                // Reload to get DB-incremented @Version — save() return is pre-commit
                refundRequest = refundRequestRepository.findById(refundRequestId).orElse(refundRequest);
            } catch (Exception e) {
                markRecoveryRequired(refundRequest, e.getMessage());
                throw new RuntimeException(
                    "Provider refund succeeded (refundId=" + refundRequest.getProviderRefundId()
                    + ") but credit note creation failed. Recovery is automatic. refundRequestId="
                    + refundRequest.getId(), e);
            }
        }

        // Apply credit note if still OPEN
        if (creditNote.getStatus() == CreditNoteStatus.OPEN) {
            try {
                creditNote = creditNoteService.applyCreditNote(creditNote.getId());
            } catch (Exception e) {
                markRecoveryRequired(refundRequest, e.getMessage());
                throw new RuntimeException(
                    "Credit note created but apply failed. Recovery is automatic. refundRequestId="
                    + refundRequest.getId(), e);
            }
        }

        // Mark payment REFUNDED
        if (succeededPayment != null && succeededPayment.getStatus() != PaymentStatus.REFUNDED) {
            succeededPayment.markRefunded();
            paymentRepository.save(succeededPayment);
        }

        // Mark invoice REFUNDED + trigger PDF regeneration when fully refunded
        if (refundRequest.getRequestedAmountMinor() >= invoice.getAmountPaid()) {
            invoiceService.markRefunded(invoice.getId(), requestedBy);
        }

        // Mark COMPLETED — reload right before save to get the latest DB version.
        // Multiple @Transactional calls above (createCreditNote, applyCreditNote, markRefunded)
        // each commit their own transaction and may bump unrelated entities in the same session,
        // so the version held in refundRequest can be stale by this point.
        RefundRequest toComplete = refundRequestRepository.findById(refundRequestId).orElse(refundRequest);
        RefundRequest completed = refundRequestRepository.save(toComplete.toBuilder()
            .status(RefundStatus.COMPLETED)
            .updatedAt(Instant.now())
            .build());
        auditEventPublisher.publish("refund.completed", completed.getTenantId(), "RefundRequest",
            completed.getId(), requestedBy, refundAuditData(completed, reason));

        log.info("Refund COMPLETED refundRequestId={} invoiceId={} amount={} creditNoteId={}",
            refundRequest.getId(), refundRequest.getInvoiceId(),
            refundRequest.getRequestedAmountMinor(), creditNote.getId());
        return creditNote;
    }

    private Map<String, Object> refundAuditData(RefundRequest request, String reason) {
        Map<String, Object> data = new HashMap<>();
        data.put("refundRequestId", request.getId().toString());
        data.put("invoiceId", request.getInvoiceId().toString());
        data.put("paymentId", request.getPaymentId() != null ? request.getPaymentId().toString() : null);
        data.put("amountMinor", request.getRequestedAmountMinor());
        data.put("providerRefundId", request.getProviderRefundId());
        data.put("reason", reason);
        return data;
    }

    private void markRecoveryRequired(RefundRequest request, String reason) {
        RefundRequest fresh = refundRequestRepository.findById(request.getId()).orElse(request);
        refundRequestRepository.save(fresh.toBuilder()
            .status(RefundStatus.RECOVERY_REQUIRED)
            .failureReason(reason)
            .updatedAt(Instant.now())
            .build());
        log.error("RefundRequest marked RECOVERY_REQUIRED refundRequestId={}: {}", request.getId(), reason);
    }

    private PlatformInvoice loadAndValidateInvoice(UUID invoiceId, UUID tenantId, long amountMinor) {
        var invoice = invoiceRepository.findById(invoiceId)
            .orElseThrow(() -> new InvoiceNotFoundException("Invoice not found: " + invoiceId));
        if (!invoice.getTenantId().equals(tenantId)) {
            throw new BusinessRuleViolationException("Invoice does not belong to tenant: " + tenantId);
        }
        if (invoice.getStatus() != InvoiceStatus.PAID) {
            throw new BusinessRuleViolationException("Can only refund PAID invoices. Status: " + invoice.getStatus());
        }
        if (amountMinor > invoice.getAmountPaid()) {
            throw new BusinessRuleViolationException(
                "Refund amount " + amountMinor + " exceeds paid amount " + invoice.getAmountPaid());
        }
        // Cumulative over-refund check: credit notes + uncommitted provider refunds
        long alreadyRefunded = sumActiveCreditNotes(tenantId, invoiceId);
        long providerCommitted = refundRequestRepository.sumProviderCommittedAmountByInvoiceId(invoiceId);
        long total = alreadyRefunded + providerCommitted + amountMinor;
        if (total > invoice.getAmountPaid()) {
            throw new BusinessRuleViolationException(
                "Refund would exceed total paid amount. Paid: " + invoice.getAmountPaid()
                + ", Already refunded (credit notes): " + alreadyRefunded
                + ", Provider-committed (pending accounting): " + providerCommitted
                + ", Requested: " + amountMinor);
        }
        return invoice;
    }

    private Payment findSucceededPayment(UUID invoiceId) {
        List<Payment> payments = paymentRepository.findByInvoiceId(invoiceId);
        return payments.stream()
            .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED && p.getExternalChargeId() != null)
            .findFirst()
            .orElse(null);
    }

    private long sumActiveCreditNotes(UUID tenantId, UUID invoiceId) {
        var filter = new CreditNoteFilter(tenantId, invoiceId, null, null, null, null);
        return creditNoteService.listCreditNotes(filter, 0, 1000, "createdAt", "DESC")
            .content().stream()
            .filter(cn -> cn.getStatus() != CreditNoteStatus.VOID)
            .mapToLong(CreditNote::getAmountMinor)
            .sum();
    }
}
