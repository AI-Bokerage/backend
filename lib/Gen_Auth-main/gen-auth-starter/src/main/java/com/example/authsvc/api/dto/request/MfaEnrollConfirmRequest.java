package com.example.authsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaEnrollConfirmRequest(
        @NotBlank @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits") String code
) {}
