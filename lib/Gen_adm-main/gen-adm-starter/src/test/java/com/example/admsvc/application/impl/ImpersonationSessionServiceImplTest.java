package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.port.ImpersonationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImpersonationSessionServiceImplTest {

    private final ImpersonationSessionRepository repository = mock(ImpersonationSessionRepository.class);
    private final ImpersonationEventPublisher eventPublisher = mock(ImpersonationEventPublisher.class);
    private ImpersonationSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImpersonationSessionServiceImpl(repository, eventPublisher);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            ImpersonationSessionEntity session = inv.getArgument(0);
            if (session.getId() == null) {
                session.setId(UUID.randomUUID());
            }
            if (session.getStatus() == null) {
                session.setStatus(ImpersonationSessionStatus.PENDING_CONSENT);
            }
            return session;
        });
    }

    private ImpersonationSessionEntity pendingSession(UUID tenantId, UUID requestedBy, UUID target, Instant expiresAt) {
        return ImpersonationSessionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .requestedByUserId(requestedBy)
                .targetUserId(target)
                .reason("support ticket #42")
                .status(ImpersonationSessionStatus.PENDING_CONSENT)
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void createRejectsRequestingToImpersonateYourself() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(tenantId, userId, userId, "why not", 30))
                .isInstanceOf(GenAdmValidationException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void createPersistsAPendingConsentSessionWithComputedExpiry() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        ImpersonationSessionEntity session = service.create(tenantId, requestedBy, target, "support ticket #42", 30);

        assertThat(session.getTenantId()).isEqualTo(tenantId);
        assertThat(session.getRequestedByUserId()).isEqualTo(requestedBy);
        assertThat(session.getTargetUserId()).isEqualTo(target);
        assertThat(session.getStatus()).isEqualTo(ImpersonationSessionStatus.PENDING_CONSENT);
        assertThat(session.getExpiresAt()).isAfter(Instant.now().plus(29, ChronoUnit.MINUTES));
    }

    @Test
    void approveFlipsToActiveAndNotifiesGranted() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ImpersonationSessionEntity approved = service.approve(tenantId, session.getId(), reviewer);

        assertThat(approved.getStatus()).isEqualTo(ImpersonationSessionStatus.ACTIVE);
        assertThat(approved.getReviewedByUserId()).isEqualTo(reviewer);
        assertThat(approved.getReviewedAt()).isNotNull();
        verify(eventPublisher).onGranted(tenantId, session.getId(), requestedBy, session.getTargetUserId());
    }

    @Test
    void approveRejectsTheRequesterApprovingTheirOwnRequest() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.approve(tenantId, session.getId(), requestedBy))
                .isInstanceOf(GenAdmForbiddenException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void approveThrowsConflictWhenSessionIsNotPendingConsent() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setStatus(ImpersonationSessionStatus.DENIED);
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.approve(tenantId, session.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void approveExpiresAnOverdueSessionInsteadOfApprovingIt() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.approve(tenantId, session.getId(), UUID.randomUUID()))
                .isInstanceOf(GenAdmConflictException.class);
        assertThat(session.getStatus()).isEqualTo(ImpersonationSessionStatus.EXPIRED);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void approveThrowsNotFoundForACrossTenantLookup() {
        UUID sessionId = UUID.randomUUID();
        when(repository.findByIdAndTenantId(eq(sessionId), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), sessionId, UUID.randomUUID()))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void rejectFlipsToDeniedAndNotifiesDenied() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ImpersonationSessionEntity rejected = service.reject(tenantId, session.getId(), reviewer);

        assertThat(rejected.getStatus()).isEqualTo(ImpersonationSessionStatus.DENIED);
        verify(eventPublisher).onDenied(tenantId, session.getId(), requestedBy, session.getTargetUserId());
    }

    @Test
    void endAllowsTheImpersonatorToEndTheirOwnSessionWithoutPrivilege() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setStatus(ImpersonationSessionStatus.ACTIVE);
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ImpersonationSessionEntity ended = service.end(tenantId, session.getId(), requestedBy, false);

        assertThat(ended.getStatus()).isEqualTo(ImpersonationSessionStatus.ENDED);
        assertThat(ended.getEndedByUserId()).isEqualTo(requestedBy);
        verify(eventPublisher).onEnded(tenantId, session.getId(), requestedBy, session.getTargetUserId());
    }

    @Test
    void endRejectsANonRequesterWithoutPrivilege() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setStatus(ImpersonationSessionStatus.ACTIVE);
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.end(tenantId, session.getId(), UUID.randomUUID(), false))
                .isInstanceOf(GenAdmForbiddenException.class);
    }

    @Test
    void endAllowsAPrivilegedActorEvenIfNotTheRequester() {
        UUID tenantId = UUID.randomUUID();
        UUID requestedBy = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, requestedBy, UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        session.setStatus(ImpersonationSessionStatus.ACTIVE);
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        ImpersonationSessionEntity ended = service.end(tenantId, session.getId(), admin, true);

        assertThat(ended.getStatus()).isEqualTo(ImpersonationSessionStatus.ENDED);
        assertThat(ended.getEndedByUserId()).isEqualTo(admin);
    }

    @Test
    void endThrowsConflictWhenSessionIsNotActive() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity session = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        when(repository.findByIdAndTenantId(session.getId(), tenantId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.end(tenantId, session.getId(), UUID.randomUUID(), true))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void listActionableFiltersOutSessionsExpiredDuringTheCall() {
        UUID tenantId = UUID.randomUUID();
        ImpersonationSessionEntity fresh = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().plus(30, ChronoUnit.MINUTES));
        ImpersonationSessionEntity overdue = pendingSession(tenantId, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now().minus(1, ChronoUnit.MINUTES));
        when(repository.findAllByTenantIdAndStatusIn(tenantId,
                List.of(ImpersonationSessionStatus.PENDING_CONSENT, ImpersonationSessionStatus.ACTIVE)))
                .thenReturn(List.of(fresh, overdue));

        List<ImpersonationSessionEntity> actionable = service.listActionable(tenantId);

        assertThat(actionable).extracting(ImpersonationSessionEntity::getId).containsExactly(fresh.getId());
        assertThat(overdue.getStatus()).isEqualTo(ImpersonationSessionStatus.EXPIRED);
    }
}
