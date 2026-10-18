package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.PaymentReconciliationService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.model.ReconciliationPolicy;
import com.company.bsmsvc.domain.enums.LedgerEntryType;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.BillingLedgerEntry;
import com.company.bsmsvc.domain.model.Payment;
import com.company.bsmsvc.domain.model.payment.PaymentStatusResult;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentReconciliationServiceImpl implements PaymentReconciliationService {

    private final PaymentRepositoryPort paymentRepository;
    private final PaymentGatewayResolver resolver;
    private final TenantBillingProfileService billingProfileService;
    private final DunningService dunningService;
    private final PlatformInvoiceRepositoryPort invoiceRepository;
    private final InvoiceService invoiceService;
    private final BillingLedgerRepositoryPort ledgerRepository;
    private final ReconciliationPolicy properties;
    private final EventPublisherPort auditEventPublisher;

    @Override
    public void reconcilePendingPayments() {
        Instant threshold = Instant.now().minusSeconds(properties.thresholdSeconds());
        List<Payment> pending = paymentRepository.findPendingOlderThan(threshold);
        if (pending.isEmpty()) {
            log.debug("Reconciliation: no pending payments older than {} seconds", properties.thresholdSeconds());
            return;
        }
        log.info("Reconciliation: found {} pending payments to check", pending.size());
        for (Payment payment : pending) {
            try {
                reconcilePayment(payment);
            } catch (Exception e) {
                log.error("Reconciliation failed for paymentId={}: {}", payment.getId(), e.getMessage());
            }
        }
    }

    @Transactional
    public void reconcilePayment(Payment payment) {
        try {
            var profile = billingProfileService.getProfile(payment.getTenantId());
            var gateway = resolver.resolve(profile.getPaymentProvider());
            PaymentStatusResult status = gateway.retrievePaymentStatus(payment.getExternalPaymentId());

            log.info("Reconciliation paymentId={} externalId={} providerStatus={}",
                payment.getId(), payment.getExternalPaymentId(), status.providerStatus());

            if (status.succeeded()) {
                payment.markSucceeded(status.chargeId());
                paymentRepository.save(payment);
                auditEventPublisher.publish("payment.captured", payment.getTenantId(),
                    "Payment", payment.getId(), null, paymentAuditData(payment, status.providerStatus()));
                try {
                    // applyPayment() fires InvoiceMarkedPaidEvent → PlatformInvoiceRepositoryAdapter
                    // writes the single authoritative INVOICE_PAID ledger entry automatically.
                    invoiceService.applyPayment(payment.getInvoiceId(), payment.getAmountMinor(), null);
                } catch (Exception ex) {
                    log.warn("Reconciliation: invoice already paid or error invoiceId={}: {}", payment.getInvoiceId(), ex.getMessage());
                }
                log.info("Reconciliation: SUCCEEDED paymentId={} invoiceId={}", payment.getId(), payment.getInvoiceId());
            } else if (status.failed()) {
                payment.markFailed("Reconciliation: provider status=" + status.providerStatus());
                paymentRepository.save(payment);
                auditEventPublisher.publish("payment.failed", payment.getTenantId(),
                    "Payment", payment.getId(), null, paymentAuditData(payment, status.providerStatus()));
                saveLedger(payment, LedgerEntryType.ADJUSTMENT, 0, "Reconciled: payment failed");
                log.info("Reconciliation: FAILED paymentId={} providerStatus={}", payment.getId(), status.providerStatus());
                // Trigger dunning only if the invoice is still unpaid.
                // Orphan sessions (expired/unpaid) that fail after the real payment
                // already paid the invoice must never push the subscription into dunning.
                try {
                    var invoice = invoiceRepository.findById(payment.getInvoiceId()).orElse(null);
                    if (invoice != null && invoice.getSubscriptionId() != null) {
                        if (invoice.isPaid()) {
                            log.info("Reconciliation: invoice already PAID — skipping dunning invoiceId={} paymentId={}",
                                invoice.getId(), payment.getId());
                        } else {
                            dunningService.startDunning(invoice.getSubscriptionId(), payment.getInvoiceId());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Reconciliation: failed to start dunning paymentId={}: {}", payment.getId(), e.getMessage());
                }
            } else {
                log.info("Reconciliation: still pending paymentId={} providerStatus={}", payment.getId(), status.providerStatus());
            }
        } catch (TenantBillingProfileNotFoundException e) {
            log.warn("Reconciliation: no billing profile for tenantId={}", payment.getTenantId());
        }
    }

    private Map<String, Object> paymentAuditData(Payment payment, String providerStatus) {
        Map<String, Object> data = new HashMap<>();
        data.put("paymentId", payment.getId().toString());
        data.put("invoiceId", payment.getInvoiceId().toString());
        data.put("externalPaymentId", payment.getExternalPaymentId());
        data.put("amountMinor", payment.getAmountMinor());
        data.put("currency", payment.getCurrency());
        data.put("providerStatus", providerStatus);
        return data;
    }

    private void saveLedger(Payment payment, LedgerEntryType type, long amount, String description) {
        ledgerRepository.save(BillingLedgerEntry.builder()
            .id(UUID.randomUUID())
            .tenantId(payment.getTenantId())
            .invoiceId(payment.getInvoiceId())
            .entryType(type)
            .amountMinor(amount)
            .currency(payment.getCurrency())
            .description(description)
            .createdAt(Instant.now())
            .build());
    }
}
