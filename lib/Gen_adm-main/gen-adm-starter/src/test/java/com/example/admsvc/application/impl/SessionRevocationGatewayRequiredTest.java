package com.example.admsvc.application.impl;

import com.example.admsvc.domain.port.OffboardingEventPublisher;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.domain.port.SessionRevocationGateway;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingJobRepository;
import com.example.admsvc.infrastructure.persistence.repository.OffboardingStepRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Proves Gen_ADM ships no default {@link SessionRevocationGateway} bean —
 * fixing source adm-svc's dead-code gap where a fully-coded
 * HttpAuthSessionRevocationGateway existed but nothing ever wired it in.
 * Uses a plain {@link AnnotationConfigApplicationContext} with mocked
 * repositories — no database, no Docker, genuinely runs in any environment.
 */
class SessionRevocationGatewayRequiredTest {

    @Configuration
    static class MissingGatewayConfig {

        @Bean
        OffboardingStepExecutor offboardingStepExecutor(OffboardingJobRepository jobRepository,
                                                          OffboardingStepRepository stepRepository,
                                                          SessionRevocationGateway gateway,
                                                          List<OffboardingStepHandler> handlers,
                                                          OffboardingEventPublisher publisher) {
            return new OffboardingStepExecutor(jobRepository, stepRepository, gateway, handlers, publisher);
        }

        @Bean
        OffboardingJobRepository jobRepository() {
            return mock(OffboardingJobRepository.class);
        }

        @Bean
        OffboardingStepRepository stepRepository() {
            return mock(OffboardingStepRepository.class);
        }

        @Bean
        OffboardingEventPublisher eventPublisher() {
            return mock(OffboardingEventPublisher.class);
        }
        // Deliberately no SessionRevocationGateway bean; an empty
        // List<OffboardingStepHandler> is valid and not under test here.
    }

    @Test
    void contextFailsToStartWithoutAConsumerSuppliedSessionRevocationGateway() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(MissingGatewayConfig.class);

        assertThatThrownBy(context::refresh)
                .isInstanceOf(UnsatisfiedDependencyException.class);
    }
}
