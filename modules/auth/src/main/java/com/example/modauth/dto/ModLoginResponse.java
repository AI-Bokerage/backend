package com.example.modauth.dto;

import com.example.modauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Either a real login result (tokens + dashboard hint) or, when "
        + "requiresTermsAcceptance is true, the \"accept updated terms\" gate with no tokens issued yet")
public record ModLoginResponse(
        UUID userId,
        String email,
        Role role,

        @Schema(description = "Which dashboard the frontend should route to",
                allowableValues = {"platform_console", "brokerage_dashboard", "team_dashboard", "broker_dashboard"})
        String dashboard,

        @Schema(description = "True when this response is the \"accept updated terms\" gate, not a real login result")
        boolean requiresTermsAcceptance,

        @Schema(description = "Present only when requiresTermsAcceptance is true")
        Integer termsVersion,

        @Schema(description = "Only populated when auth.token-delivery-mode=json — otherwise delivered as an HttpOnly cookie")
        String accessToken,

        @Schema(description = "Only populated when auth.token-delivery-mode=json — otherwise delivered as an HttpOnly cookie")
        String refreshToken,

        Instant accessTokenExpiresAt
) {
    public static ModLoginResponse termsGate(int termsVersion) {
        return new ModLoginResponse(null, null, null, null, true, termsVersion, null, null, null);
    }
}
