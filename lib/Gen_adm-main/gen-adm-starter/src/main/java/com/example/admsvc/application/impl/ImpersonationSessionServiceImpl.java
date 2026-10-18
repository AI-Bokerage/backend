package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.ImpersonationSessionService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.port.ImpersonationEventPublisher;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.example.admsvc.infrastructure.persistence.repository.ImpersonationSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class ImpersonationSessionServiceImpl implements ImpersonationSessionService {

    private final ImpersonationSessionRepository repository;
    private final ImpersonationEventPublisher eventPublisher;

    public ImpersonationSessionServiceImpl(ImpersonationSessionRepository repository,
                                            ImpersonationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public ImpersonationSessionEntity create(UUID tenantId, UUID requestedByUserId, UUID targetUserId,
                                              String reason, int ttlMinutes) {
        if (requestedByUserId.equals(targetUserId)) {
            throw new GenAdmValidationException("Cannot request to impersonate yourself");
        }
        return repository.saveAndFlush(ImpersonationSessionEntity.builder()
                .tenantId(tenantId)
                .requestedByUserId(requestedByUserId)
                .targetUserId(targetUserId)
                .reason(reason)
                .expiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .build());
    }

    @Override
    @Transactional
    public List<ImpersonationSessionEntity> listActionable(UUID tenantId) {
        return repository.findAllByTenantIdAndStatusIn(tenantId,
                        List.of(ImpersonationSessionStatus.PENDING_CONSENT, ImpersonationSessionStatus.ACTIVE))
                .stream()
                .map(this::expireIfOverdue)
                .filter(session -> session.getStatus() != ImpersonationSessionStatus.EXPIRED)
                .toList();
    }

    @Override
    @Transactional
    public ImpersonationSessionEntity approve(UUID tenantId, UUID sessionId, UUID reviewerId) {
        ImpersonationSessionEntity session = expireIfOverdue(findOrThrow(tenantId, sessionId));
        if (session.getStatus() != ImpersonationSessionStatus.PENDING_CONSENT) {
            throw new GenAdmConflictException("Impersonation session is not awaiting consent: " + sessionId);
        }
        if (reviewerId.equals(session.getRequestedByUserId())) {
            throw new GenAdmForbiddenException("Cannot approve your own impersonation request");
        }
        session.setStatus(ImpersonationSessionStatus.ACTIVE);
        session.setReviewedByUserId(reviewerId);
        session.setReviewedAt(Instant.now());
        ImpersonationSessionEntity saved = repository.saveAndFlush(session);
        eventPublisher.onGranted(tenantId, saved.getId(), saved.getRequestedByUserId(), saved.getTargetUserId());
        return saved;
    }

    @Override
    @Transactional
    public ImpersonationSessionEntity reject(UUID tenantId, UUID sessionId, UUID reviewerId) {
        ImpersonationSessionEntity session = expireIfOverdue(findOrThrow(tenantId, sessionId));
        if (session.getStatus() != ImpersonationSessionStatus.PENDING_CONSENT) {
            throw new GenAdmConflictException("Impersonation session is not awaiting consent: " + sessionId);
        }
        if (reviewerId.equals(session.getRequestedByUserId())) {
            throw new GenAdmForbiddenException("Cannot reject your own impersonation request");
        }
        session.setStatus(ImpersonationSessionStatus.DENIED);
        session.setReviewedByUserId(reviewerId);
        session.setReviewedAt(Instant.now());
        ImpersonationSessionEntity saved = repository.saveAndFlush(session);
        eventPublisher.onDenied(tenantId, saved.getId(), saved.getRequestedByUserId(), saved.getTargetUserId());
        return saved;
    }

    @Override
    @Transactional
    public ImpersonationSessionEntity end(UUID tenantId, UUID sessionId, UUID actorId, boolean privileged) {
        ImpersonationSessionEntity session = expireIfOverdue(findOrThrow(tenantId, sessionId));
        if (session.getStatus() != ImpersonationSessionStatus.ACTIVE) {
            throw new GenAdmConflictException("Impersonation session is not active: " + sessionId);
        }
        if (!privileged && !actorId.equals(session.getRequestedByUserId())) {
            throw new GenAdmForbiddenException("Only the impersonator or a privileged reviewer can end this session");
        }
        session.setStatus(ImpersonationSessionStatus.ENDED);
        session.setEndedByUserId(actorId);
        session.setEndedAt(Instant.now());
        ImpersonationSessionEntity saved = repository.saveAndFlush(session);
        eventPublisher.onEnded(tenantId, saved.getId(), saved.getRequestedByUserId(), saved.getTargetUserId());
        return saved;
    }

    private ImpersonationSessionEntity findOrThrow(UUID tenantId, UUID sessionId) {
        return repository.findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new GenAdmNotFoundException("Impersonation session not found: " + sessionId));
    }

    private ImpersonationSessionEntity expireIfOverdue(ImpersonationSessionEntity session) {
        boolean pendingOrActive = session.getStatus() == ImpersonationSessionStatus.PENDING_CONSENT
                || session.getStatus() == ImpersonationSessionStatus.ACTIVE;
        if (pendingOrActive && Instant.now().isAfter(session.getExpiresAt())) {
            session.setStatus(ImpersonationSessionStatus.EXPIRED);
            return repository.saveAndFlush(session);
        }
        return session;
    }
}
