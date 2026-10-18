package com.example.admsvc.config;

import com.example.admsvc.domain.port.OffboardingEventPublisher;
import com.example.admsvc.infrastructure.event.NoOpOffboardingEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OffboardingConfig {

    @Bean
    @ConditionalOnMissingBean(OffboardingEventPublisher.class)
    public OffboardingEventPublisher offboardingEventPublisher() {
        return new NoOpOffboardingEventPublisher();
    }
}
