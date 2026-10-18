package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;

import java.time.Instant;
import java.util.UUID;

public record OffboardingJobResponse(UUID id, UUID userId, String status, int attemptCount,
                                      Instant nextRetryAt, Instant completedAt) {

    public static OffboardingJobResponse from(OffboardingJobEntity job) {
        return new OffboardingJobResponse(
                job.getId(),
                job.getUserId(),
                job.getStatus().name(),
                job.getAttemptCount(),
                job.getNextRetryAt(),
                job.getCompletedAt());
    }
}
