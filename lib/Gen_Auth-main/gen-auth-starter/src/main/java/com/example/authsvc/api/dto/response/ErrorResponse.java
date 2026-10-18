package com.example.authsvc.api.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "API error response")
public class ErrorResponse {

    @Schema(description = "Human-readable error message", example = "Invalid credentials")
    private String message;
}
