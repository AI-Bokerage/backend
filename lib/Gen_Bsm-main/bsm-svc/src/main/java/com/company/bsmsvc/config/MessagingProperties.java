package com.company.bsmsvc.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "messaging.invoice")
@Getter
@Setter
public class MessagingProperties {
    private String eventsExchange = "cpms.events";
    private String dlxExchange = "cpms.tasks.dlx";
    private String invoiceCreatedRoutingKey = "bsm.invoice.created";
    private String invoicePdfQueue = "q.bsm.invoice-pdf-generation";
    private String invoicePdfDlq = "q.dlq.q.bsm.invoice-pdf-generation";
    private String tenantCreatedQueue = "q.bsm.tenant-created";
    private String tenantCreatedDlq = "q.dlq.bsm.tenant-created";
}
