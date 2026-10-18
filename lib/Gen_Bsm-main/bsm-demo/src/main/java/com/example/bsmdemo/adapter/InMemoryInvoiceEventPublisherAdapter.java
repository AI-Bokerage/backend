package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.InvoiceCreatedMessage;
import com.company.bsmsvc.domain.port.InvoiceEventPublisher;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InMemoryInvoiceEventPublisherAdapter implements InvoiceEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryInvoiceEventPublisherAdapter.class);

    private final List<InvoiceCreatedMessage> published = new CopyOnWriteArrayList<>();

    @Override
    public void publishInvoiceCreated(InvoiceCreatedMessage message) {
        log.info("Invoice created event published: invoiceId={} tenantId={} invoiceNumber={}",
            message.getInvoiceId(), message.getTenantId(), message.getInvoiceNumber());
        published.add(message);
    }

    public List<InvoiceCreatedMessage> getPublished() {
        return published;
    }
}
