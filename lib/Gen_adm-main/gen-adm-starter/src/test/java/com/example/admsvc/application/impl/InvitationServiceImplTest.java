package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.domain.enums.InvitationStatus;
import com.example.admsvc.domain.port.InvitationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;
import com.example.admsvc.infrastructure.persistence.repository.InvitationRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceImplTest {

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private InvitationEventPublisher eventPublisher;

    @Mock
    private InvitationAcceptanceExecutor acceptanceExecutor;

    private InvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        GenAdmProperties properties = new GenAdmProperties();
        properties.setInvitationTtlDays(7);
        service = new InvitationServiceImpl(invitationRepository, roleRepository, eventPublisher, properties, acceptanceExecutor);
        lenient().when(invitationRepository.saveAndFlush(any())).thenAnswer(inv -> {
            InvitationEntity invitation = inv.getArgument(0);
            if (invitation.getId() == null) {
                invitation.setId(UUID.randomUUID());
            }
            if (invitation.getStatus() == null) {
                invitation.setStatus(InvitationStatus.PENDING);
            }
            return invitation;
        });
    }

    @Test
    void createRejectsARoleThatDoesNotExistInTheTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsByIdAndTenantId(roleId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(tenantId, UUID.randomUUID(), "a@example.com", List.of(roleId)))
                .isInstanceOf(GenAdmValidationException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createPersistsAPendingInvitationAndFiresOnCreated() {
        UUID tenantId = UUID.randomUUID();
        UUID invitedBy = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsByIdAndTenantId(roleId, tenantId)).thenReturn(true);
        when(invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, "a@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.empty());

        InvitationEntity invitation = service.create(tenantId, invitedBy, "a@example.com", List.of(roleId));

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invitation.getEmail()).isEqualTo("a@example.com");
        assertThat(invitation.getInvitedByUserId()).isEqualTo(invitedBy);
        assertThat(invitation.getRoleIds()).containsExactly(roleId);
        assertThat(invitation.getExpiresAt()).isAfter(Instant.now().plus(6, ChronoUnit.DAYS));
        assertThat(invitation.getPlaintextToken()).isNotBlank();
        verify(eventPublisher).onCreated(eq(tenantId), any(), eq("a@example.com"), eq(invitation.getPlaintextToken()));
    }

    @Test
    void createRejectsADuplicatePendingInvitationForTheSameEmail() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsByIdAndTenantId(roleId, tenantId)).thenReturn(true);
        InvitationEntity existing = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).email("a@example.com")
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, "a@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(tenantId, UUID.randomUUID(), "a@example.com", List.of(roleId)))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void createAllowsANewInvitationWhenThePriorOneForThatEmailHasExpired() {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        when(roleRepository.existsByIdAndTenantId(roleId, tenantId)).thenReturn(true);
        InvitationEntity overdue = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).email("a@example.com")
                .status(InvitationStatus.PENDING).expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByTenantIdAndEmailAndStatus(tenantId, "a@example.com", InvitationStatus.PENDING))
                .thenReturn(Optional.of(overdue));

        InvitationEntity invitation = service.create(tenantId, UUID.randomUUID(), "a@example.com", List.of(roleId));

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(overdue.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void listActionableFiltersOutInvitationsExpiredDuringTheCall() {
        UUID tenantId = UUID.randomUUID();
        InvitationEntity fresh = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        InvitationEntity overdue = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findAllByTenantIdAndStatus(tenantId, InvitationStatus.PENDING))
                .thenReturn(List.of(fresh, overdue));

        List<InvitationEntity> actionable = service.listActionable(tenantId);

        assertThat(actionable).extracting(InvitationEntity::getId).containsExactly(fresh.getId());
        assertThat(overdue.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void cancelFlipsAPendingInvitationToCancelled() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID cancelledBy = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByIdAndTenantId(invitationId, tenantId)).thenReturn(Optional.of(invitation));

        InvitationEntity cancelled = service.cancel(tenantId, invitationId, cancelledBy);

        assertThat(cancelled.getStatus()).isEqualTo(InvitationStatus.CANCELLED);
        assertThat(cancelled.getCancelledByUserId()).isEqualTo(cancelledBy);
    }

    @Test
    void cancelThrowsConflictWhenNotPending() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.CANCELLED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByIdAndTenantId(invitationId, tenantId)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.cancel(tenantId, invitationId, UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void acceptThrowsNotFoundForAnUnknownToken() {
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept("bogus-token", UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
        verifyNoInteractions(acceptanceExecutor);
    }

    @Test
    void acceptExpiresAnOverdueInvitationAndThrowsConflict() {
        InvitationEntity invitation = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept("some-token", UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
        verifyNoInteractions(acceptanceExecutor);
    }

    @Test
    void acceptThrowsConflictWhenNotPending() {
        InvitationEntity invitation = InvitationEntity.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).status(InvitationStatus.CANCELLED)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> service.accept("some-token", UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
        verifyNoInteractions(acceptanceExecutor);
    }

    @Test
    void acceptDelegatesToExecutorForAValidPendingInvitation() {
        UUID tenantId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        InvitationEntity invitation = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS)).build();
        InvitationEntity accepted = InvitationEntity.builder()
                .id(invitationId).tenantId(tenantId).status(InvitationStatus.ACCEPTED).build();
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));
        when(acceptanceExecutor.completeAcceptance(tenantId, invitationId, userId)).thenReturn(accepted);

        InvitationEntity result = service.accept("some-token", userId);

        assertThat(result.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        verify(acceptanceExecutor).completeAcceptance(tenantId, invitationId, userId);
    }
}
