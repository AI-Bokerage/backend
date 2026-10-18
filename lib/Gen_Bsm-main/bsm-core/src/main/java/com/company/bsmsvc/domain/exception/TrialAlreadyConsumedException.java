package com.company.bsmsvc.domain.exception;

import java.util.UUID;

public class TrialAlreadyConsumedException extends BusinessRuleViolationException {

    public TrialAlreadyConsumedException(UUID tenantId) {
        super("Tenant " + tenantId + " has already consumed their lifetime free trial. "
            + "Trial subscriptions are granted once per tenant and cannot be re-issued "
            + "after cancellation or expiry.");
    }
}
