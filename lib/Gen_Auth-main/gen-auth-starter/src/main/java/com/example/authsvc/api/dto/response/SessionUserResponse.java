package com.example.authsvc.api.dto.response;

import com.example.authsvc.domain.enums.UserType;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight user projection inside the session response.
 * Exposes only the fields the frontend needs for routing and display.
 *
 * <p>{@code roleIds} is the authoritative multi-role list (Phase 4+).
 * {@code roleId} is retained for backward compatibility and equals
 * {@code roleIds.isEmpty() ? null : roleIds.get(0)}.
 */
public record SessionUserResponse(
        UUID       id,
        UUID       tenantId,
        String     email,
        UserType   userType,
        UUID       roleId,       // backward-compat scalar — first element of roleIds
        List<UUID> roleIds       // full multi-role list
) {}
