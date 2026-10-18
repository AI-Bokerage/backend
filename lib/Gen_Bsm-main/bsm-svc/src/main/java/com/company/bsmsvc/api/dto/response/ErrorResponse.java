package com.company.bsmsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Error response payload")
public record ErrorResponse(
    Instant timestamp,
    String code,
    String message,
    String path
) {
}
