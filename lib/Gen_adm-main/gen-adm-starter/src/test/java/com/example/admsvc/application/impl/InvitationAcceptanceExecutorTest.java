package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationAcceptanceExecutorTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private UserRoleAssignmentService userRoleAssignmentService;

    private InvitationAcceptanceExecutor executor;

    // NOT a field initializer: MockitoExtension populates @Mock fields via
    // postProcessTestInstance, which runs after field initializers but
    // before @BeforeEach — building `executor` inline above would capture
    // both dependencies as null.
    @BeforeEach
    void setUp() {
        executor = new InvitationAcceptanceExecutor(invitationRepository, userRoleAssignmentService);
    }

    @Test
    void assignsEveryRoleAndFlipsToAccepted() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        UUID roleB = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        invitation.setRoleIds(List.of(roleA, roleB));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(invitationRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        InvitationEntity result = executor.completeAcceptance(tenantId, invitationId, userId);

        assertThat(result.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(result.getAcceptedAt()).isNotNull();
        verify(userRoleAssignmentService).assignRoleFromInvitation(tenantId, userId, roleA);
        verify(userRoleAssignmentService).assignRoleFromInvitation(tenantId, userId, roleB);
    }

    @Test
    void throwsNotFoundWhenInvitationIsMissing() {
        UUID invitationId = UUID.randomUUID();
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> executor.completeAcceptance(UUID.randomUUID(), invitationId, UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void aMidLoopAssignmentFailurePreventsTheAcceptedFlip() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID roleA = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        invitation.setRoleIds(List.of(roleA));
        when(invitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        doThrow(new RuntimeException("boom")).when(userRoleAssignmentService)
                .assignRoleFromInvitation(tenantId, userId, roleA);

        assertThatThrownBy(() -> executor.completeAcceptance(tenantId, invitationId, userId))
                .isInstanceOf(RuntimeException.class);

        verify(invitationRepository, never()).saveAndFlush(any());
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
    }
}
