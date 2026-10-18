package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;

import java.util.List;
import java.util.UUID;

public interface InvitationService {

    InvitationEntity create(UUID tenantId, UUID invitedByUserId, String email, List<UUID> roleIds);

    List<InvitationEntity> listActionable(UUID tenantId);

    InvitationEntity cancel(UUID tenantId, UUID invitationId, UUID cancelledByUserId);

    InvitationEntity accept(String token, UUID userId);
}
