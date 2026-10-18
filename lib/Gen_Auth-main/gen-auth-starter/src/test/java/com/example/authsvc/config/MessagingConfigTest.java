package com.example.authsvc.config;

import com.example.authsvc.config.properties.MessagingProperties;
import com.example.authsvc.infrastructure.messaging.outbox.AuthOutboxRelayJob;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.persistence.repository.AuthOutboxEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessagingConfigTest {

    @Configuration
    @ConfigurationPropertiesScan("com.example.authsvc.config.properties")
    static class PropertiesTestConfig {}

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RabbitAutoConfiguration.class))
            .withBean(AuthOutboxEventJpaRepository.class, () -> mock(AuthOutboxEventJpaRepository.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(PropertiesTestConfig.class, MessagingConfig.class,
                    AuthEventPublisher.class, AuthOutboxRelayJob.class);

    @Test
    void messagingDisabled_noExchangeOrPublisherBean() {
        contextRunner
                .withPropertyValues("app.messaging.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TopicExchange.class);
                    assertThat(context).doesNotHaveBean(AuthEventPublisher.class);
                    assertThat(context).doesNotHaveBean(AuthOutboxRelayJob.class);
                });
    }

    @Test
    void messagingEnabled_exchangeAndPublisherBeanPresent() {
        contextRunner
                .withPropertyValues("app.messaging.enabled=true", "app.messaging.exchange=auth.events")
                .run(context -> {
                    java.util.Collection<TopicExchange> topicExchanges = context.getBeansOfType(TopicExchange.class).values();
                    assertThat(topicExchanges).extracting(TopicExchange::getName)
                            .containsExactlyInAnyOrder("auth.events", "auth.audit.tenant");
                    assertThat(context).hasSingleBean(FanoutExchange.class);
                    assertThat(context.getBean(FanoutExchange.class).getName()).isEqualTo("auth.audit.platform");
                    assertThat(context).hasSingleBean(AuthEventPublisher.class);
                    assertThat(context.getBean(MessageConverter.class)).isInstanceOf(JacksonJsonMessageConverter.class);
                    assertThat(context).hasSingleBean(AuthOutboxRelayJob.class);
                });
    }
}
