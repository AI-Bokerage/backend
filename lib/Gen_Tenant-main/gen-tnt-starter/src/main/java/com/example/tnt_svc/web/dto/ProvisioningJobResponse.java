// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/ProvisioningJobResponse.java
package com.example.tnt_svc.web.dto;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;

import java.time.Instant;
import java.util.UUID;

public record ProvisioningJobResponse(
    UUID id,
    UUID tenantId,
    ProvisioningJobStatus status,
    int retryCount,
    String lastError,
    Instant startedAt,
    Instant completedAt,
    Instant expiresAt
) {
    public static ProvisioningJobResponse from(ProvisioningJob job) {
        return new ProvisioningJobResponse(
            job.getId(), job.getTenantId(), job.getStatus(), job.getRetryCount(),
            job.getLastError(), job.getStartedAt(), job.getCompletedAt(), job.getExpiresAt());
    }
}
