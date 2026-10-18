package com.company.bsmsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single warning generated during downgrade preflight analysis")
public record DowngradeWarningDto(

    @Schema(description = "Machine-readable warning code (e.g. USERS_OVER_LIMIT)")
    String code,

    @Schema(description = "Human-readable warning message")
    String message
) {
}
