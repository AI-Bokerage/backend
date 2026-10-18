package com.example.admsvc.config;

import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.event.NoOpInvitationEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InvitationConfig {

    @Bean
    @ConditionalOnMissingBean(InvitationEventPublisher.class)
    public InvitationEventPublisher invitationEventPublisher() {
        return new NoOpInvitationEventPublisher();
    }
}
