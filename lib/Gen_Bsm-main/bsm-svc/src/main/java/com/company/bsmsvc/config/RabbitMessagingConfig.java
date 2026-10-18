package com.company.bsmsvc.config;

import io.cpms.common.messaging.AuditMessagingTopology;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@code @Import(AuditMessagingTopology.class)} (docs/audit/15 fix): {@link
 * com.company.bsmsvc.infrastructure.outbox.BsmOutboxPublisher} publishes bsm-svc's audit leg to
 * {@code cpms.audit} (via {@link com.company.bsmsvc.infrastructure.outbox.BsmAuditEventRouter})
 * but this config never declared that exchange bean itself — same fix, same rationale as
 * pmt-svc's identical gap.
 */
@Configuration
@RequiredArgsConstructor
@Import(AuditMessagingTopology.class)
public class RabbitMessagingConfig {

    private final MessagingProperties messagingProperties;

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(messagingProperties.getEventsExchange(), true, false);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(messagingProperties.getDlxExchange(), true, false);
    }

    @Bean
    public Queue invoicePdfQueue() {
        return QueueBuilder.durable(messagingProperties.getInvoicePdfQueue())
            .withArgument("x-dead-letter-exchange", messagingProperties.getDlxExchange())
            .withArgument("x-dead-letter-routing-key", messagingProperties.getInvoicePdfDlq())
            .build();
    }

    @Bean
    public Queue invoicePdfDlq() {
        return QueueBuilder.durable(messagingProperties.getInvoicePdfDlq()).build();
    }

    @Bean
    public Binding invoiceCreatedBinding(TopicExchange eventsExchange, Queue invoicePdfQueue) {
        return BindingBuilder.bind(invoicePdfQueue).to(eventsExchange).with(messagingProperties.getInvoiceCreatedRoutingKey());
    }

    @Bean
    public Binding invoicePdfDlqBinding(TopicExchange dlxExchange, Queue invoicePdfDlq) {
        return BindingBuilder.bind(invoicePdfDlq).to(dlxExchange).with(messagingProperties.getInvoicePdfDlq());
    }
    // ── Tenant-created consumer queue (TNT → BSM) ────────────────────────────

    @Bean
    public Queue tenantCreatedQueue() {
        return QueueBuilder.durable(messagingProperties.getTenantCreatedQueue())
            .withArgument("x-dead-letter-exchange", messagingProperties.getDlxExchange())
            .withArgument("x-dead-letter-routing-key", messagingProperties.getTenantCreatedDlq())
            .build();
    }

    @Bean
    public Queue tenantCreatedDlq() {
        return QueueBuilder.durable(messagingProperties.getTenantCreatedDlq()).build();
    }

    @Bean
    public Binding tenantCreatedBinding(TopicExchange eventsExchange, Queue tenantCreatedQueue) {
        return BindingBuilder.bind(tenantCreatedQueue).to(eventsExchange).with("tenant.created");
    }

    @Bean
    public Binding tenantCreatedDlqBinding(DirectExchange dlxExchange, Queue tenantCreatedDlq) {
        return BindingBuilder.bind(tenantCreatedDlq).to(dlxExchange).with(messagingProperties.getTenantCreatedDlq());
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }}
