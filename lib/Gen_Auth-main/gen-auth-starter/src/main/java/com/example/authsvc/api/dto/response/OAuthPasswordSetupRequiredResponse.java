package com.example.authsvc.api.dto.response;

public record OAuthPasswordSetupRequiredResponse(
        boolean passwordSetupRequired,
        String setupToken
) {}
