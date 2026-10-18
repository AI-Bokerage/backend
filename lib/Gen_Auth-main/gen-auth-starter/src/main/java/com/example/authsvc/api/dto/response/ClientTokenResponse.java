package com.example.authsvc.api.dto.response;

public record ClientTokenResponse(
        String accessToken,
        int    expiresIn,
        String sessionId
) {}
