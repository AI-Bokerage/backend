package com.company.bsmsvc.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Standard API response")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    @Schema(example = "true")
    boolean success,

    @Schema(example = "Operation completed successfully")
    String message,

    T data,

    PaginationMetadata pagination
) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> ok(String message, T data, PaginationMetadata pagination) {
        return new ApiResponse<>(true, message, data, pagination);
    }

    public static <T> ApiResponse<T> error(String message, T data) {
        return new ApiResponse<>(false, message, data, null);
    }
}
