package com.example.authsvc.application.service;

import com.example.authsvc.api.dto.response.ServiceTokenResponse;

public interface ServiceTokenService {

    /**
     * Mints a short-lived, stateless service-account JWT for internal
     * service-to-service calls. No session is persisted.
     *
     * @param callerService the caller's own service name, for logging only —
     *                      never used as an authorization check
     */
    ServiceTokenResponse issue(String callerService);
}
