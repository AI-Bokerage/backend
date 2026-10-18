package io.cpms.common.errors;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        boolean success,
        ErrorBody error,
        String requestId
) {
    public record ErrorBody(String code, String message, Object details) {}

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(false, new ErrorBody(code, message, null), null);
    }

    public static ApiErrorResponse of(String code, String message, Object details) {
        return new ApiErrorResponse(false, new ErrorBody(code, message, details), null);
    }

    public static ApiErrorResponse of(String code, String message, Object details, String requestId) {
        return new ApiErrorResponse(false, new ErrorBody(code, message, details), requestId);
    }
}
