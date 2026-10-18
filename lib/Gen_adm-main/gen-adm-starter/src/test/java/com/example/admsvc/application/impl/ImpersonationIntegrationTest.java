package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.ImpersonationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = ImpersonationIntegrationTest.TestApp.class)
class ImpersonationIntegrationTest {

    @SpringBootApplication
    static class TestApp {

        @Bean
        RecordingImpersonationEventPublisher recordingImpersonationEventPublisher() {
            return new RecordingImpersonationEventPublisher();
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static class RecordingImpersonationEventPublisher implements ImpersonationEventPublisher {
        final List<String> events = new ArrayList<>();

        @Override
        public void onGranted(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
            events.add("GRANTED");
        }

        @Override
        public void onDenied(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
            events.add("DENIED");
        }

        @Override
        public void onEnded(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
            events.add("ENDED");
        }
    }

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private ImpersonationSessionServiceImpl impersonationSessionService;

    @Autowired
    private RecordingImpersonationEventPublisher eventPublisher;

    // A Spring singleton bean under this class's cached context, so its
    // event list persists across test methods. Reset before every test so
    // each method is independent regardless of run order.
    @BeforeEach
    void resetRecordedEvents() {
        eventPublisher.events.clear();
    }

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void requestApproveThenSelfEndFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, target, "support ticket #42", 30);
        assertThat(created.getStatus()).isEqualTo(ImpersonationSessionStatus.PENDING_CONSENT);

        ImpersonationSessionEntity approved = impersonationSessionService.approve(tenantId, created.getId(), reviewer);
        assertThat(approved.getStatus()).isEqualTo(ImpersonationSessionStatus.ACTIVE);

        ImpersonationSessionEntity ended = impersonationSessionService.end(tenantId, created.getId(), requestedBy, false);
        assertThat(ended.getStatus()).isEqualTo(ImpersonationSessionStatus.ENDED);
        assertThat(ended.getEndedByUserId()).isEqualTo(requestedBy);

        assertThat(eventPublisher.events).containsExactly("GRANTED", "ENDED");
    }

    @Test
    void requestThenRejectFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, UUID.randomUUID(), "support ticket #43", 30);

        ImpersonationSessionEntity rejected = impersonationSessionService.reject(tenantId, created.getId(), reviewer);

        assertThat(rejected.getStatus()).isEqualTo(ImpersonationSessionStatus.DENIED);
        assertThat(eventPublisher.events).containsExactly("DENIED");
    }

    @Test
    void selfApprovalIsRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, UUID.randomUUID(), "support ticket #44", 30);

        assertThatThrownBy(() -> impersonationSessionService.approve(tenantId, created.getId(), requestedBy))
                .isInstanceOf(GenAdmForbiddenException.class);
    }

    @Test
    void crossTenantLookupIs404() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, UUID.randomUUID(), "support ticket #45", 30);

        assertThatThrownBy(() -> impersonationSessionService.approve(
                UUID.randomUUID(), created.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void anAlreadyExpiredRequestCannotBeApproved() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        authenticateAs(tenantId, requestedBy);

        // ttlMinutes <= 0 is rejected by CreateImpersonationRequest's @Min(1)
        // at the REST boundary, but the service itself has no lower bound
        // (see Global Constraints) — used here to build an overdue row
        // deterministically instead of sleeping in a test.
        ImpersonationSessionEntity created = impersonationSessionService.create(
                tenantId, requestedBy, UUID.randomUUID(), "support ticket #46", -5);

        assertThatThrownBy(() -> impersonationSessionService.approve(tenantId, created.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);

        ImpersonationSessionEntity reloaded = impersonationSessionService.listActionable(tenantId).stream()
                .filter(s -> s.getId().equals(created.getId()))
                .findFirst()
                .orElse(null);
        assertThat(reloaded).isNull(); // EXPIRED sessions are filtered out of the actionable list
    }
}
