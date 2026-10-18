package com.example.authsvc.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record MagicLinkIssueRequest(
        @NotBlank @Email String email,
        /** Optional tenant slug — when present the reset link uses {slug}.{root} instead of root. */
        String tenantSlug
) {}
