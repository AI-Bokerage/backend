package com.example.admsvc.infrastructure.event;

import com.example.admsvc.domain.port.OffboardingEventPublisher;

import java.util.UUID;

public class NoOpOffboardingEventPublisher implements OffboardingEventPublisher {

    @Override
    public void onCompleted(UUID tenantId, UUID userId) {
    }

    @Override
    public void onFailed(UUID tenantId, UUID userId, String reason) {
    }
}
