package com.example.admsvc.infrastructure.event;

import com.example.admsvc.domain.port.InvitationEventPublisher;

import java.util.UUID;

public class NoOpInvitationEventPublisher implements InvitationEventPublisher {

    @Override
    public void onCreated(UUID tenantId, UUID invitationId, String email, String plaintextToken) {
    }
}
