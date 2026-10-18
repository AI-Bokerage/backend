package io.cpms.common.errors;

public record ErrorResponse(String code, String message, Object details) {}
