package com.example.gendemo;

import com.example.admsvc.domain.port.OffboardingStepHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sample optional step showing how a consumer plugs in additional
 * offboarding behavior beyond the mandatory session-revocation step.
 */
@Component
public class DemoNotifyStepHandler implements OffboardingStepHandler {

    private static final Logger log = LoggerFactory.getLogger(DemoNotifyStepHandler.class);

    @Override
    public String stepName() {
        return "NOTIFY_MANAGER";
    }

    @Override
    public void handle(UUID tenantId, UUID userId, UUID initiatedBy) {
        log.info("Notifying manager that user {} was offboarded (initiated by {})", userId, initiatedBy);
    }
}
