package com.company.bsmsvc.messaging;

import com.company.bsmsvc.domain.model.InvoiceCreatedMessage;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.storage.InvoiceDocumentStoragePort;
import com.company.bsmsvc.pdf.InvoicePdfGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfGenerationConsumer {

    private final com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort invoiceRepository;
    private final InvoicePdfGenerator invoicePdfGenerator;
    private final InvoiceDocumentStoragePort documentStorage;

    @RabbitListener(queues = "${messaging.invoice.invoice-pdf-queue:q.bsm.invoice-pdf-generation}")
    public void handleInvoiceCreated(InvoiceCreatedMessage message) {
        log.info("[handleInvoiceCreated] invoiceId={}", message.getInvoiceId());
        java.util.UUID id = message.getInvoiceId();
        PlatformInvoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new com.company.bsmsvc.domain.exception.BusinessRuleViolationException("Invoice not found: " + id));

        if (invoice.getPdfGenerationStatus() == com.company.bsmsvc.domain.enums.InvoicePdfStatus.GENERATED
            || invoice.getPdfUrl() != null) {
            log.info("[handleInvoiceCreated] invoiceId={} already has generated pdf, skipping", id);
            return;
        }

        try {
            invoice.markPdfProcessing();
            invoice = invoiceRepository.save(invoice);

            byte[] pdf = invoicePdfGenerator.generatePdf(invoice);
            String url = documentStorage.uploadInvoicePdf(invoice, pdf, "application/pdf");

            // Persist generated state with optimistic-lock retry to handle concurrent updates
            int attempts = 0;
            final int maxAttempts = 3;
            while (true) {
                try {
                    invoice.markPdfGenerated(url);
                    invoice = invoiceRepository.save(invoice);
                    log.info("[handleInvoiceCreated] PDF generated and uploaded for invoiceId={}", id);
                    break;
                } catch (org.springframework.dao.OptimisticLockingFailureException ole) {
                    attempts++;
                    if (attempts >= maxAttempts) {
                        log.error("[handleInvoiceCreated] Optimistic lock persist failed after {} attempts for invoiceId={}", attempts, id, ole);
                        throw ole;
                    }
                    log.warn("[handleInvoiceCreated] Optimistic lock detected for invoiceId={}, retrying {}/{}", id, attempts, maxAttempts);
                    // reload latest state and retry
                    invoice = invoiceRepository.findById(id)
                        .orElseThrow(() -> new com.company.bsmsvc.domain.exception.BusinessRuleViolationException("Invoice not found: " + id));
                }
            }
        } catch (Exception ex) {
            log.error("[handleInvoiceCreated] PDF generation failed for invoiceId={}", id, ex);
            try {
                invoice = invoiceRepository.findById(id)
                    .orElseThrow(() -> new com.company.bsmsvc.domain.exception.BusinessRuleViolationException("Invoice not found: " + id));
                invoice.markPdfFailed();
                invoiceRepository.save(invoice);
            } catch (Exception saveEx) {
                log.error("[handleInvoiceCreated] Failed to persist failure state for invoiceId={}", id, saveEx);
            }
            // Let exception propagate so RabbitMQ retry/dead-letter can handle
            throw new RuntimeException(ex);
        }
    }
}
