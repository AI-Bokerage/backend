package com.example.modauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Platform-triggered brokerage-owner invite — called by modules/platform
 * right after it creates the Gen_TNT tenant record, not by an authenticated
 * modauth user. Secured the same way as every other {@code /internal/**}
 * endpoint (shared-secret header via gen-auth-starter's own
 * InternalTokenAuthFilter), not by role hierarchy.
 */
@Schema(description = "Creates a TENANT_ADMIN (brokerage owner) invitation on the platform's behalf")
public record InternalCreateInvitationRequest(
        @NotBlank String name,
        @NotBlank @Email String email,

        @NotNull
        @Schema(description = "The Gen_TNT tenant id this brokerage owner belongs to")
        UUID tenantId,

        @NotNull
        @Schema(description = "The user id Gen_TNT already recorded as primaryOwnerUserId — "
                + "accept() creates the auth_users row with this exact id")
        UUID preAllocatedUserId
) {
}
