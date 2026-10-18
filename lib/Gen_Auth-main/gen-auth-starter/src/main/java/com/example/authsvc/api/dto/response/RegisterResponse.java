package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "Response body returned after successful registration")
public record RegisterResponse(
        @Schema(description = "UUID of the newly created user")
        UUID userId
) {}
