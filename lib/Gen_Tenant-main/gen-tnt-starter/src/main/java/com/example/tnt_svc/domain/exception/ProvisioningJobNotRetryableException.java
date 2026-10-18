package com.example.tnt_svc.domain.exception;

import com.example.tnt_svc.domain.ProvisioningJobStatus;

import java.util.UUID;

public class ProvisioningJobNotRetryableException extends RuntimeException {
    public ProvisioningJobNotRetryableException(UUID jobId, ProvisioningJobStatus status) {
        super("Job " + jobId + " is not retryable in status " + status + " (only FAILED jobs can be retried)");
    }
}
