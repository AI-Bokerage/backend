package com.company.ppmsvc.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Generic API response envelope used by every PPM controller endpoint.
 *
 * <pre>{@code
 * // Success
 * { "success": true,  "message": "Plan created", "data": { ... } }
 *
 * // Error (data omitted)
 * { "success": false, "message": "Plan not found" }
 * }</pre>
 *
 * @param <T> the type of the {@code data} payload
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response envelope")
public final class ApiResponse<T> {

    @Schema(description = "true when the operation succeeded")
    private final boolean success;

    @Schema(description = "Human-readable result message")
    private final String message;

    @Schema(description = "Response payload; absent on error responses")
    private final T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, "Created successfully", data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> noContent() {
        return (ApiResponse<T>) new ApiResponse<>(true, "No content", null);
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> error(String message) {
        return (ApiResponse<T>) new ApiResponse<>(false, message, null);
    }

    public static <T> ApiResponse<T> errorWithData(String message, T data) {
        return new ApiResponse<>(false, message, data);
    }
}
