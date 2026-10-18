package com.example.tnt_svc.domain.exception;

import java.util.UUID;

public class ProvisioningLockException extends RuntimeException {
    public ProvisioningLockException(UUID tenantId) {
        super("Could not acquire provisioning lock for tenant: " + tenantId);
    }
}
