package com.example.modauth.dto;

import com.example.modauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the "accept an invitation" screen shows before the person sets a
 * password — the {@code GET /invitations/{token}} response. Returning this
 * at all implies the link is still valid; an expired/unknown/already-used
 * token is a 4xx instead (see InvitationController).
 */
@Schema(description = "Invitation preview shown on the \"set your password\" screen, before it's accepted")
public record InvitationPreviewResponse(
        String name,
        String email,
        Role role,
        String teamName
) {
}
