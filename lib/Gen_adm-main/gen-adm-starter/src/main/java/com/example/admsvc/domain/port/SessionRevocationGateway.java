package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Revokes an offboarded user's active sessions. Mandatory — Gen_ADM ships no
 * default implementation and no no-op fallback, unlike source adm-svc where a
 * fully-coded {@code HttpAuthSessionRevocationGateway} existed but was never
 * actually invoked by the step executor. A consumer that omits a bean for
 * this port fails to start, by design.
 */
public interface SessionRevocationGateway {

    void revoke(UUID tenantId, UUID userId);
}
