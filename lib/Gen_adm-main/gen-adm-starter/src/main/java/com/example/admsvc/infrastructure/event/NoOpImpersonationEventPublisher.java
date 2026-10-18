package com.example.admsvc.infrastructure.event;

import com.example.admsvc.domain.port.ImpersonationEventPublisher;

import java.util.UUID;

public class NoOpImpersonationEventPublisher implements ImpersonationEventPublisher {

    @Override
    public void onGranted(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
    }

    @Override
    public void onDenied(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
    }

    @Override
    public void onEnded(UUID tenantId, UUID sessionId, UUID requestedByUserId, UUID targetUserId) {
    }
}
