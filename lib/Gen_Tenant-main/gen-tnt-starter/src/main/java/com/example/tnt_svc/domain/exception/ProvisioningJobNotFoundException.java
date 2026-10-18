package com.example.tnt_svc.domain.exception;

import java.util.UUID;

public class ProvisioningJobNotFoundException extends RuntimeException {
    public ProvisioningJobNotFoundException(UUID id) {
        super("Provisioning job not found: " + id);
    }
}
