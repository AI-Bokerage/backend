package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;

import java.util.List;
import java.util.UUID;

public interface ImpersonationSessionService {

    ImpersonationSessionEntity create(UUID tenantId, UUID requestedByUserId, UUID targetUserId,
                                       String reason, int ttlMinutes);

    List<ImpersonationSessionEntity> listActionable(UUID tenantId);

    ImpersonationSessionEntity approve(UUID tenantId, UUID sessionId, UUID reviewerId);

    ImpersonationSessionEntity reject(UUID tenantId, UUID sessionId, UUID reviewerId);

    ImpersonationSessionEntity end(UUID tenantId, UUID sessionId, UUID actorId, boolean privileged);
}
