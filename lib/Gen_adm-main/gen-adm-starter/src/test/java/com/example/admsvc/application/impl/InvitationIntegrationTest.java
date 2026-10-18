package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = InvitationIntegrationTest.TestApp.class)
class InvitationIntegrationTest {

    @SpringBootApplication
    static class TestApp {

        @Bean
        RecordingInvitationEventPublisher recordingInvitationEventPublisher() {
            return new RecordingInvitationEventPublisher();
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static class RecordingInvitationEventPublisher implements InvitationEventPublisher {
        final List<String> events = new ArrayList<>();

        @Override
        public void onCreated(UUID tenantId, UUID invitationId, String email, String plaintextToken) {
            events.add("CREATED:" + email);
        }
    }

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private InvitationServiceImpl invitationService;

    @Autowired
    private InvitationRepository invitationRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleAssignmentRepository assignmentRepository;

    @Autowired
    private RecordingInvitationEventPublisher eventPublisher;

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

    private UUID createRole(UUID tenantId) {
        return roleRepository.saveAndFlush(RoleEntity.builder()
                .tenantId(tenantId).name("member-" + UUID.randomUUID()).build()).getId();
    }

    @Test
    void createAcceptFlowAssignsRolesAndMarksAccepted() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        authenticateAs(tenantId, invitedBy);
        UUID roleId = createRole(tenantId);

        InvitationEntity created = invitationService.create(tenantId, invitedBy, "new@example.com", List.of(roleId));
        assertThat(created.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(created.getPlaintextToken()).isNotBlank();
        assertThat(eventPublisher.events).containsExactly("CREATED:new@example.com");

        // accept is a public, token-authenticated endpoint — prove it works
        // with no principal in scope at all, not just that one happens to
        // be left over from the create() call above.
        SecurityContextHolder.clearContext();
        UUID newUserId = UUID.randomUUID();
        InvitationEntity accepted = invitationService.accept(created.getPlaintextToken(), newUserId);

        assertThat(accepted.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        List<UserRoleAssignmentEntity> assignments = assignmentRepository.findAllByTenantIdAndUserId(tenantId, newUserId);
        assertThat(assignments).extracting(UserRoleAssignmentEntity::getRoleId).containsExactly(roleId);
    }

    @Test
    void createCancelFlow() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        authenticateAs(tenantId, invitedBy);
        UUID roleId = createRole(tenantId);

        InvitationEntity created = invitationService.create(tenantId, invitedBy, "cancel@example.com", List.of(roleId));
        InvitationEntity cancelled = invitationService.cancel(tenantId, created.getId(), invitedBy);

        assertThat(cancelled.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(invitationService.listActionable(tenantId)).isEmpty();
    }

    @Test
    void duplicatePendingInvitationForTheSameEmailIsRejected() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        authenticateAs(tenantId, invitedBy);
        UUID roleId = createRole(tenantId);
        invitationService.create(tenantId, invitedBy, "dup@example.com", List.of(roleId));

        assertThatThrownBy(() -> invitationService.create(tenantId, invitedBy, "dup@example.com", List.of(roleId)))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void anExpiredInvitationCannotBeAccepted() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        authenticateAs(tenantId, invitedBy);
        UUID roleId = createRole(tenantId);
        InvitationEntity created = invitationService.create(tenantId, invitedBy, "expired@example.com", List.of(roleId));

        InvitationEntity stored = invitationRepository.findById(created.getId()).orElseThrow();
        stored.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        invitationRepository.saveAndFlush(stored);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> invitationService.accept(created.getPlaintextToken(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void acceptingAnUnknownTokenIs404() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> invitationService.accept("no-such-token", UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }
}
