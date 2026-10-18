package com.example.authsvc.api.dto.request;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /v1/client-token}.
 *
 * <p>The calling internal service (e.g. CPT-SVC) is expected to have already
 * verified the human — this service is a pure token factory.
 */
public record ClientTokenRequest(
        @NotNull UUID clientUserId,
        @NotNull UUID tenantId,
        @Nullable String tenantSlug,
        @NotNull UUID roleId,
        @NotBlank String sessionId
) {}
