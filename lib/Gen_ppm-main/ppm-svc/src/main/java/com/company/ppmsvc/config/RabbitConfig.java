package com.company.ppmsvc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology and template configuration for PPM-SVC.
 *
 * <p>All domain events are published to the topic exchange named by
 * {@code messaging.exchanges.cpms} (see application.yaml). Routing keys
 * follow the pattern {@code ppm.<aggregate>.<verb>}.
 *
 * <p>Consumer queues and DLQ topology will be declared per-feature as PPM-SVC
 * grows. Only the shared exchange and publisher infrastructure is declared here.
 */
@Slf4j
@Configuration
public class RabbitConfig {

    @Bean
    public TopicExchange cpmsEventsExchange(
            @Value("${messaging.exchanges.cpms}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);

        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.warn("Broker NACK: messageId={} cause={}",
                    correlationData != null ? correlationData.getId() : "null", cause);
            }
        });

        template.setReturnsCallback(returned ->
            log.warn("Message returned unrouted: exchange={} routingKey={} replyCode={} replyText={}",
                returned.getExchange(), returned.getRoutingKey(),
                returned.getReplyCode(), returned.getReplyText())
        );

        return template;
    }

    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
