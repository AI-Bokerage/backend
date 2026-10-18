package com.example.modauth.dto;

import com.example.modauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "The newly created account and where the frontend should send it next")
public record AcceptInvitationResponse(
        UUID userId,
        String email,
        Role role,
        String dashboard,

        @Schema(description = "\"complete_brokerage_profile\" for a Tenant Admin, "
                + "\"connect_email_calendar_optional\" otherwise",
                allowableValues = {"complete_brokerage_profile", "connect_email_calendar_optional"})
        String nextStep
) {
}
