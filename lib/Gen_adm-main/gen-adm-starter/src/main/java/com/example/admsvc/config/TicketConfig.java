package com.example.admsvc.config;

import com.example.admsvc.domain.port.TicketEventPublisher;
import com.example.admsvc.infrastructure.event.NoOpTicketEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TicketConfig {

    @Bean
    @ConditionalOnMissingBean(TicketEventPublisher.class)
    public TicketEventPublisher ticketEventPublisher() {
        return new NoOpTicketEventPublisher();
    }
}
