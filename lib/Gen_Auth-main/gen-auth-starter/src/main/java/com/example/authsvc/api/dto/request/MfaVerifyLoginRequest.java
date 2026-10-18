package com.example.authsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record MfaVerifyLoginRequest(
        @NotBlank String challengeToken,
        @NotBlank String code
) {}
