package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;

import java.util.UUID;

public interface OffboardingService {

    OffboardingJobEntity initiate(UUID tenantId, UUID userId, UUID initiatedBy, String reason);

    OffboardingJobEntity getJob(UUID tenantId, UUID jobId);

    OffboardingJobEntity retry(UUID tenantId, UUID jobId);
}
