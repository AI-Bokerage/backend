package com.company.bsmsvc.messaging;

import com.company.bsmsvc.domain.model.InvoiceCreatedMessage;
import com.company.bsmsvc.domain.port.InvoiceEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RabbitInvoiceEventPublisher implements InvoiceEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final com.company.bsmsvc.config.MessagingProperties messagingProperties;

    @Override
    public void publishInvoiceCreated(InvoiceCreatedMessage message) {
        String exchange = messagingProperties.getEventsExchange();
        String routingKey = messagingProperties.getInvoiceCreatedRoutingKey();
        log.info("[publishInvoiceCreated] exchange={} routingKey={} invoiceId={}", exchange, routingKey, message.getInvoiceId());
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
    }
}
