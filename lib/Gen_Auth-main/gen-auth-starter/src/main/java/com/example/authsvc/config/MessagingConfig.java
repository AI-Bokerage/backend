package com.example.authsvc.config;

import com.example.authsvc.config.properties.AuditRoutingProperties;
import com.example.authsvc.config.properties.MessagingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Declares only the exchange events publish to — no queues, no bindings.
 * Consuming applications own their own queue/binding topology; that is not
 * this starter's concern. Only active when {@code app.messaging.enabled=true}.
 *
 * <p>Also registers a JSON {@link MessageConverter} bean. Historically this
 * converter sat in the critical path for every publish (Boot auto-detects a
 * single {@code MessageConverter} bean and wires it into the auto-configured
 * {@code RabbitTemplate} — see {@code RabbitTemplateConfigurer.setMessageConverter}
 * in {@code spring-boot-amqp}'s {@code RabbitAutoConfiguration}). That is no
 * longer true: {@code AuthEventPublisher} no longer calls
 * {@code rabbitTemplate.convertAndSend(...)} at all — it writes rows to the
 * {@code auth_outbox_events} table — and
 * {@code AuthOutboxRelayJob} sends pre-serialized JSON bytes directly via
 * {@code rabbitTemplate.send(...)}, bypassing this converter entirely. The
 * bean is still registered here — harmless to leave in place, and Spring
 * Boot's autoconfiguration may still reference it for other purposes — but it
 * is no longer load-bearing for the outbox-based publishing flow.
 */
@Configuration
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@EnableScheduling
public class MessagingConfig {

    private final MessagingProperties messagingProperties;
    private final AuditRoutingProperties auditRoutingProperties;

    @Bean
    public TopicExchange authEventsExchange() {
        return new TopicExchange(messagingProperties.getExchange());
    }

    @Bean
    public TopicExchange auditTenantExchange() {
        return new TopicExchange(auditRoutingProperties.getTenantExchange());
    }

    @Bean
    public FanoutExchange auditPlatformExchange() {
        return new FanoutExchange(auditRoutingProperties.getPlatformExchange());
    }

    // Wire contract: JacksonJsonMessageConverter serializes java.time.Instant
    // fields as ISO-8601 strings, not epoch numbers — matters to any future
    // consumer of these events, not just this starter.
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
