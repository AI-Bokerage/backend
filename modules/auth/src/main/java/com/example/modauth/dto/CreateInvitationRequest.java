package com.example.modauth.dto;

import com.example.modauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Invite a Tenant Admin, Team Lead or Broker into the caller's brokerage. "
        + "Who can invite whom follows the platform hierarchy — see InvitationServiceImpl.requireCanInvite.")
public record CreateInvitationRequest(
        @NotBlank
        @Schema(example = "Jordan Lee", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @NotBlank @Email
        @Schema(example = "jordan.lee@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotNull
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Role role,

        @Schema(description = "An existing team (same brokerage) to place a BROKER invitee into. Optional — "
                + "a Broker can also be added to a team later, and a Team Lead has no team until one is "
                + "created for them.")
        UUID teamId
) {
}
