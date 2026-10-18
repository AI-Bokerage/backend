package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.InvitationEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvitationResponse(
        UUID id,
        UUID tenantId,
        String email,
        String status,
        List<UUID> roleIds,
        UUID invitedByUserId,
        Instant expiresAt,
        Instant acceptedAt,
        Instant cancelledAt,
        UUID cancelledByUserId,
        Instant createdAt,
        String token) {

    public static InvitationResponse from(InvitationEntity invitation) {
        return build(invitation, null);
    }

    public static InvitationResponse forCreate(InvitationEntity invitation, String plaintextToken) {
        return build(invitation, plaintextToken);
    }

    private static InvitationResponse build(InvitationEntity invitation, String token) {
        return new InvitationResponse(
                invitation.getId(),
                invitation.getTenantId(),
                invitation.getEmail(),
                invitation.getStatus().name(),
                invitation.getRoleIds(),
                invitation.getInvitedByUserId(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getCancelledAt(),
                invitation.getCancelledByUserId(),
                invitation.getCreatedAt(),
                token);
    }
}
