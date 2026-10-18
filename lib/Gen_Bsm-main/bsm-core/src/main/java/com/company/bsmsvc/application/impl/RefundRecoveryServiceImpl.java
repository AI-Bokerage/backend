package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.CreditNoteService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.RefundRecoveryService;
import com.company.bsmsvc.domain.enums.CreditNoteStatus;
import com.company.bsmsvc.domain.enums.PaymentStatus;
import com.company.bsmsvc.domain.enums.RefundStatus;
import com.company.bsmsvc.domain.model.CreditNote;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.RefundRequestRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefundRecoveryServiceImpl implements RefundRecoveryService {

    private final RefundRequestRepositoryPort refundRequestRepository;
    private final PlatformInvoiceRepositoryPort invoiceRepository;
    private final PaymentRepositoryPort paymentRepository;
    private final CreditNoteService creditNoteService;
    private final InvoiceService invoiceService;
    private final EventPublisherPort auditEventPublisher;

    @Override
    public void recoverAll() {
        // Collect both states: RECOVERY_REQUIRED (local work failed) and
        // PROVIDER_REFUND_SUCCEEDED (step-3 succeeded but completeLocalWork + markRecoveryRequired
        // both failed, leaving the request stranded with providerRefundId already committed).
        List<com.company.bsmsvc.domain.model.RefundRequest> toRecover = new ArrayList<>(
            refundRequestRepository.findByStatus(RefundStatus.RECOVERY_REQUIRED));
        // Only pick up PROVIDER_REFUND_SUCCEEDED records older than 30 seconds.
        // This gives the main API thread time to complete local work before recovery races with it.
        java.time.Instant graceCutoff = java.time.Instant.now().minusSeconds(30);
        toRecover.addAll(
            refundRequestRepository.findByStatusOlderThan(RefundStatus.PROVIDER_REFUND_SUCCEEDED, graceCutoff));

        if (toRecover.isEmpty()) return;
        log.info("RefundRecovery: found {} requests to recover (RECOVERY_REQUIRED + PROVIDER_REFUND_SUCCEEDED)",
            toRecover.size());
        for (var request : toRecover) {
            try {
                recover(request.getId());
            } catch (Exception e) {
                log.error("RefundRecovery: failed for refundRequestId={}: {}", request.getId(), e.getMessage());
            }
        }
    }

    @Override
    public void recover(UUID refundRequestId) {
        var request = refundRequestRepository.findById(refundRequestId)
            .orElseThrow(() -> new IllegalArgumentException("RefundRequest not found: " + refundRequestId));

        if (request.getStatus() == RefundStatus.COMPLETED) {
            log.info("RefundRecovery: already COMPLETED, skipping refundRequestId={}", refundRequestId);
            return;
        }
        if (request.getStatus() != RefundStatus.RECOVERY_REQUIRED
            && request.getStatus() != RefundStatus.PROVIDER_REFUND_SUCCEEDED) {
            log.info("RefundRecovery: not recoverable, status={} refundRequestId={}", request.getStatus(), refundRequestId);
            return;
        }

        log.info("RefundRecovery: recovering refundRequestId={} invoiceId={} providerRefundId={}",
            refundRequestId, request.getInvoiceId(), request.getProviderRefundId());

        var invoice = invoiceRepository.findById(request.getInvoiceId()).orElse(null);
        if (invoice == null) {
            log.error("RefundRecovery: invoice not found invoiceId={} refundRequestId={}",
                request.getInvoiceId(), refundRequestId);
            return;
        }

        // Step 1: Ensure credit note exists (idempotent)
        CreditNote creditNote;
        if (request.getCreditNoteId() != null) {
            try {
                creditNote = creditNoteService.getCreditNoteById(request.getCreditNoteId());
                log.info("RefundRecovery: credit note already exists creditNoteId={}", creditNote.getId());
            } catch (Exception e) {
                log.warn("RefundRecovery: creditNoteId on request not found, re-creating. refundRequestId={}", refundRequestId);
                creditNote = createCreditNote(request, invoice);
            }
        } else {
            creditNote = createCreditNote(request, invoice);
        }

        // Step 2: Apply credit note if still OPEN
        if (creditNote.getStatus() == CreditNoteStatus.OPEN) {
            try {
                creditNote = creditNoteService.applyCreditNote(creditNote.getId());
                log.info("RefundRecovery: applied credit note creditNoteId={}", creditNote.getId());
            } catch (Exception e) {
                log.warn("RefundRecovery: failed to apply credit note creditNoteId={}: {}", creditNote.getId(), e.getMessage());
            }
        }

        // Step 3: Mark payment as REFUNDED (idempotent)
        if (request.getPaymentId() != null) {
            paymentRepository.findById(request.getPaymentId()).ifPresent(payment -> {
                if (payment.getStatus() != PaymentStatus.REFUNDED) {
                    payment.markRefunded();
                    paymentRepository.save(payment);
                    log.info("RefundRecovery: marked payment REFUNDED paymentId={}", payment.getId());
                }
            });
        }

        // Mark invoice REFUNDED + trigger PDF regeneration when fully refunded
        if (request.getRequestedAmountMinor() >= invoice.getAmountPaid()
                && invoice.getStatus() != com.company.bsmsvc.domain.enums.InvoiceStatus.REFUNDED) {
            invoiceService.markRefunded(invoice.getId(), null);
            log.info("RefundRecovery: marked invoice REFUNDED invoiceId={}", invoice.getId());
        }

        // Step 4: Mark RefundRequest COMPLETED — reload to get the DB-incremented version,
        // since createCreditNote() may have saved an intermediate update above.
        var fresh = refundRequestRepository.findById(refundRequestId).orElse(request);
        var completed = refundRequestRepository.save(fresh.toBuilder()
            .creditNoteId(creditNote.getId())
            .status(RefundStatus.COMPLETED)
            .updatedAt(Instant.now())
            .build());
        // NEW audit-only leg — automated recovery path, no actor threaded through here today.
        Map<String, Object> data = new HashMap<>();
        data.put("refundRequestId", completed.getId().toString());
        data.put("invoiceId", completed.getInvoiceId().toString());
        data.put("creditNoteId", creditNote.getId().toString());
        data.put("amountMinor", completed.getRequestedAmountMinor());
        data.put("providerRefundId", completed.getProviderRefundId());
        data.put("reason", "recovery");
        auditEventPublisher.publish("refund.completed", completed.getTenantId(), "RefundRequest",
            completed.getId(), null, data);

        log.info("RefundRecovery: COMPLETED refundRequestId={} creditNoteId={}", refundRequestId, creditNote.getId());
    }

    private CreditNote createCreditNote(com.company.bsmsvc.domain.model.RefundRequest request,
                                         com.company.bsmsvc.domain.model.PlatformInvoice invoice) {
        String reason = "Recovery: provider refund " + request.getProviderRefundId();
        CreditNote cn = creditNoteService.createCreditNote(
            request.getTenantId(), request.getInvoiceId(),
            request.getRequestedAmountMinor(), invoice.getCurrency(),
            reason, request.getTenantId());
        // Save the creditNoteId immediately so we don't create duplicates on re-run
        refundRequestRepository.save(request.toBuilder()
            .creditNoteId(cn.getId())
            .updatedAt(Instant.now())
            .build());
        log.info("RefundRecovery: created credit note creditNoteId={} refundRequestId={}", cn.getId(), request.getId());
        return cn;
    }
}
