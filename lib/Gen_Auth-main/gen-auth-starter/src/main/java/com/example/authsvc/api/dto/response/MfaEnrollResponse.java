package com.example.authsvc.api.dto.response;

public record MfaEnrollResponse(
        String otpauthUri,
        String secret
) {}
