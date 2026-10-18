package com.company.bsmsvc.application.scheduler;

import com.company.bsmsvc.application.service.InvoiceRenewalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvoiceRenewalScheduler {

    private final InvoiceRenewalService invoiceRenewalService;

    @Scheduled(fixedDelayString = "${invoice.renewal.interval-ms:3600000}")
    public void runRenewal() {
        log.debug("InvoiceRenewalScheduler: starting renewal run");
        try {
            invoiceRenewalService.generateDueRenewalInvoices();
        } catch (Exception e) {
            log.error("InvoiceRenewalScheduler: renewal run failed", e);
        }
    }
}
