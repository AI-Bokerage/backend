package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "All kids currently in the JWT signing key registry")
public record KeyListResponse(
        @Schema(description = "The kid currently used to sign new tokens")
        String activeKid,
        @Schema(description = "Every kid in the registry, including retired-but-not-yet-removed ones")
        List<String> kids
) {}
