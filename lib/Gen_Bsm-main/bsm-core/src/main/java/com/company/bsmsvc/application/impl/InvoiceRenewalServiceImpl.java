package com.company.bsmsvc.application.impl;

import com.company.bsmsvc.application.service.InvoiceRenewalService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import com.company.bsmsvc.domain.enums.BillingCycle;
import com.company.bsmsvc.domain.enums.InvoiceSource;
import com.company.bsmsvc.domain.enums.InvoicePdfStatus;
import com.company.bsmsvc.domain.enums.InvoiceStatus;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceRenewalServiceImpl implements InvoiceRenewalService {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final InvoiceService invoiceService;
    private final PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort;
    private final TenantBillingProfileService billingProfileService;
    private final SubscriptionEventPublisherPort subscriptionEventPublisher;

    @Override
    public void generateDueRenewalInvoices() {
        Instant now = Instant.now();
        List<Subscription> due = subscriptionRepository.findDueForRenewal(now);
        if (due.isEmpty()) {
            log.debug("InvoiceRenewal: no subscriptions due for renewal at {}", now);
            return;
        }
        log.info("InvoiceRenewal: {} subscriptions due for renewal", due.size());
        for (Subscription sub : due) {
            Instant periodStart = sub.getCurrentPeriodEnd();
            Instant periodEnd = computePeriodEnd(periodStart, sub.getBillingCycle());

            // Pre-check: if a recurring invoice already exists for this billing period, skip
            // creation entirely and just ensure the subscription period is advanced.
            if (platformInvoiceRepositoryPort.existsRecurringBySubscriptionIdAndPeriodStartAndPeriodEnd(
                    sub.getId(), periodStart, periodEnd)) {
                log.info("Recurring invoice already exists for billing period; advancing subscription.");
                tryAdvancePeriod(sub);
                continue;
            }

            try {
                generateRenewalInvoice(sub);
            } catch (Exception e) {
                log.error("InvoiceRenewal: failed for subscriptionId={}: {}", sub.getId(), e.getMessage(), e);
            }
        }
    }

    @Override
    @Transactional
    public PlatformInvoice generateRenewalInvoice(Subscription subscription) {
        Instant periodStart = subscription.getCurrentPeriodEnd();
        Instant periodEnd = computePeriodEnd(periodStart, subscription.getBillingCycle());
        LocalDate dueDate = periodStart.plus(7, ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate();

        // Build a PlatformInvoice shell — line items are auto-populated by InvoiceServiceImpl
        PlatformInvoice shell = PlatformInvoice.builder()
            .id(UUID.randomUUID())
            .tenantId(subscription.getTenantId())
            .subscriptionId(subscription.getId())
            .currency(resolveCurrency(subscription))
            .periodStart(periodStart)
            .periodEnd(periodEnd)
            .dueDate(dueDate)
            .source(InvoiceSource.SUBSCRIPTION_RENEWAL)
            .status(InvoiceStatus.DRAFT)
            .amountDue(0L)
            .amountPaid(0L)
            .lineItems(new ArrayList<>())
            .domainEvents(new ArrayList<>())
            .pdfGenerationStatus(InvoicePdfStatus.PENDING)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        PlatformInvoice generated = invoiceService.createInvoice(shell);

        // Advance the subscription's billing period so findDueForRenewal stops returning it.
        // If this save fails, the next scheduler run hits the pre-check above and advances the
        // period there without attempting another creation.
        try {
            subscription.advanceBillingPeriod(periodStart, periodEnd);
            subscriptionRepository.save(subscription);
            log.info("InvoiceRenewal: advanced period subscriptionId={} newPeriodEnd={}", subscription.getId(), periodEnd);
        } catch (Exception e) {
            log.error("InvoiceRenewal: period advancement failed for subscriptionId={} — will self-heal on next run: {}",
                subscription.getId(), e.getMessage());
        }

        log.info("InvoiceRenewal: generated invoiceId={} invoiceNumber={} subscriptionId={}",
            generated.getId(), generated.getInvoiceNumber(), subscription.getId());

        // Publish renewal event to BSM outbox so TNT billing snapshot stays current
        try {
            subscriptionEventPublisher.publishRenewed(subscription);
        } catch (Exception e) {
            log.warn("InvoiceRenewal: failed to publish renewal event for subscriptionId={}: {}",
                subscription.getId(), e.getMessage());
        }
        return generated;
    }

    private Instant computePeriodEnd(Instant periodStart, BillingCycle cycle) {
        return cycle == BillingCycle.YEARLY
            ? periodStart.plus(365, ChronoUnit.DAYS)
            : periodStart.plus(30, ChronoUnit.DAYS);
    }

    private String resolveCurrency(Subscription subscription) {
        return billingProfileService.getProfile(subscription.getTenantId()).getCurrency();
    }

    // Called when the billing period is already covered and only the subscription period
    // advancement needs to be retried (period save failed on a previous run).
    private void tryAdvancePeriod(Subscription sub) {
        try {
            Instant newPeriodStart = sub.getCurrentPeriodEnd();
            Instant newPeriodEnd   = computePeriodEnd(newPeriodStart, sub.getBillingCycle());
            sub.advanceBillingPeriod(newPeriodStart, newPeriodEnd);
            subscriptionRepository.save(sub);
            log.info("InvoiceRenewal: advanced period subscriptionId={} newPeriodEnd={}", sub.getId(), newPeriodEnd);
        } catch (Exception e) {
            log.warn("InvoiceRenewal: period advancement failed subscriptionId={}: {}", sub.getId(), e.getMessage());
        }
    }
}
