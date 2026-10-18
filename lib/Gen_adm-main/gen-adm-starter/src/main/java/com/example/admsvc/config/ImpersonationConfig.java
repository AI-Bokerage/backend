package com.example.admsvc.config;

import com.example.admsvc.domain.port.ImpersonationEventPublisher;
import com.example.admsvc.infrastructure.event.NoOpImpersonationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImpersonationConfig {

    @Bean
    @ConditionalOnMissingBean(ImpersonationEventPublisher.class)
    public ImpersonationEventPublisher impersonationEventPublisher() {
        return new NoOpImpersonationEventPublisher();
    }
}
