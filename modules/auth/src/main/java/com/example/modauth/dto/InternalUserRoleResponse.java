package com.example.modauth.dto;

import com.example.modauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Service-to-service counterpart of {@link PersonResponse} — what a caller
 * outside modules/auth (namely modules/platform's CRM module) needs to
 * authorize a Broker/Team Lead/Tenant Admin request, since the JWT itself
 * only carries {@code user_type}, not this module's own {@link Role}.
 */
@Schema(description = "This person's platform role/team, looked up by user id — for service-to-service authorization")
public record InternalUserRoleResponse(
        UUID userId,
        UUID tenantId,
        Role role,
        UUID teamId,
        String name,
        boolean active,
        @Schema(description = "The person's brokerage is suspended or cancelled — block every request (Flow 1)")
        boolean tenantSuspended
) {
}
