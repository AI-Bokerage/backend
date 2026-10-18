package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * One pluggable offboarding step beyond the mandatory session-revocation
 * step. A consumer registers zero or more Spring beans implementing this
 * interface; {@code OffboardingServiceImpl} orders them after session
 * revocation in the order Spring injects the {@code List<OffboardingStepHandler>}.
 */
public interface OffboardingStepHandler {

    String stepName();

    void handle(UUID tenantId, UUID userId, UUID initiatedBy);
}
