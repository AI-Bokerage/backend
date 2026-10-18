package com.example.tnt_svc.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningJobTest {

    private ProvisioningJob newJob() {
        return ProvisioningJob.builder()
            .tenantId(UUID.randomUUID())
            .status(ProvisioningJobStatus.PENDING)
            .retryCount(0)
            .maxRetries(3)
            .callbackToken(UUID.randomUUID().toString())
            .context(Map.of())
            .build();
    }

    @Test
    void canRetryIsTrueWhenFailedAndUnderMax() {
        ProvisioningJob job = newJob();
        job.markInProgress();
        job.markFailed("boom");
        assertThat(job.canRetry()).isTrue();
    }

    @Test
    void canRetryIsFalseWhenRetryCountReachesMax() {
        ProvisioningJob job = newJob();
        job.markInProgress();
        job.markFailed("boom");
        job.incrementRetry();
        job.incrementRetry();
        job.incrementRetry();
        assertThat(job.canRetry()).isFalse();
    }

    @Test
    void canRetryIsFalseWhenNotFailed() {
        ProvisioningJob job = newJob();
        assertThat(job.canRetry()).isFalse();
    }

    @Test
    void mergeContextAddsKeysWithoutDroppingExisting() {
        ProvisioningJob job = newJob();
        job.mergeContext(Map.of("a", "1"));
        job.mergeContext(Map.of("b", "2"));
        assertThat(job.getContext()).containsEntry("a", "1").containsEntry("b", "2");
    }

    @Test
    void markCompletedSetsCompletedAt() {
        ProvisioningJob job = newJob();
        job.markInProgress();
        job.markCompleted();
        assertThat(job.getStatus()).isEqualTo(ProvisioningJobStatus.COMPLETED);
        assertThat(job.getCompletedAt()).isNotNull();
    }
}
