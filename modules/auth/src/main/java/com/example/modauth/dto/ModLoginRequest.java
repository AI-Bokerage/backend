package com.example.modauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Login credentials for the role-aware login endpoint")
public record ModLoginRequest(
        @NotBlank @Email
        @Schema(example = "tenant.admin@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank
        @Schema(example = "••••••••", requiredMode = Schema.RequiredMode.REQUIRED)
        String password,

        // Boxed (Boolean, not boolean): Jackson 3's record deserializer throws
        // MismatchedInputException for a missing primitive field instead of
        // defaulting it — confirmed by an actual 500 when a client simply
        // omitted acceptTerms on a first login attempt. The compact
        // constructor below normalizes a missing/null value to false, so
        // every other caller in the codebase can still treat this as a plain
        // non-null boolean.
        @Schema(description = "Resend as true, with the same credentials, once the user has been shown the "
                + "updated terms and agreed — see the \"have the terms changed since last login?\" gate. "
                + "Leave false/absent on a first attempt.", defaultValue = "false")
        Boolean acceptTerms
) {
    public ModLoginRequest {
        acceptTerms = acceptTerms != null && acceptTerms;
    }
}
