package com.company.bsmsvc.infrastructure.client.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Response of AUTH-SVC's POST /internal/service-token. Not envelope-wrapped. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthServiceTokenResult(
    String accessToken,
    int    expiresIn
) {}
