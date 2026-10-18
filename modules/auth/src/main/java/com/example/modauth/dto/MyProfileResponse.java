package com.example.modauth.dto;

import com.example.modauth.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "The signed-in person's own profile (Flow 4.12 \"My profile\")")
public record MyProfileResponse(
        UUID userId,
        @Schema(description = "Sign-in email — changed only by an admin re-inviting, not here") String email,
        String name,
        String phone,
        Role role,
        UUID teamId,
        String teamName
) {
}
