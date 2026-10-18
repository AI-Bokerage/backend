package com.example.gendemo;

import com.example.admsvc.domain.port.SessionRevocationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * FOR LOCAL DEMO ONLY — a real host application calls its own auth service's
 * session-revocation endpoint here (e.g. Gen_AUTH's
 * {@code /internal/auth/users/{userId}/revoke-sessions}). Gen_ADM requires a
 * bean for this port; startup fails without one.
 */
@Component
public class DemoSessionRevocationGateway implements SessionRevocationGateway {

    private static final Logger log = LoggerFactory.getLogger(DemoSessionRevocationGateway.class);

    @Override
    public void revoke(UUID tenantId, UUID userId) {
        log.info("Revoking sessions for user {} in tenant {}", userId, tenantId);
    }
}
