package com.example.admsvc.application.impl;

import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.OffboardingStepHandler;
import com.example.admsvc.domain.port.SessionRevocationGateway;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full flow: initiate -> afterCommit-triggered execution -> a forced first
 * failure on a pluggable step -> manual retry -> resumed completion. The
 * FlakyStepHandler fails once then succeeds, proving the executor resumes
 * from the failed step rather than re-running the already-COMPLETED
 * session-revocation step.
 */
@Testcontainers
@SpringBootTest(classes = OffboardingIntegrationTest.TestApp.class)
class OffboardingIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Component
    static class RecordingSessionRevocationGateway implements SessionRevocationGateway {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public void revoke(UUID tenantId, UUID userId) {
            calls.incrementAndGet();
        }
    }

    @Component
    static class FlakyStepHandler implements OffboardingStepHandler {
        final AtomicInteger attempts = new AtomicInteger();

        @Override
        public String stepName() {
            return "FLAKY_STEP";
        }

        @Override
        public void handle(UUID tenantId, UUID userId, UUID initiatedBy) {
            if (attempts.incrementAndGet() == 1) {
                throw new RuntimeException("simulated failure");
            }
        }
    }

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    @Autowired
    private OffboardingServiceImpl offboardingService;

    @Autowired
    private RecordingSessionRevocationGateway sessionRevocationGateway;

    @Autowired
    private FlakyStepHandler flakyStepHandler;

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    // Both @Component beans above are Spring singletons under this class's cached
    // context, so their AtomicInteger state persists across test methods. Reset
    // before every test so each method is independent regardless of run order.
    @BeforeEach
    void resetSharedComponentState() {
        sessionRevocationGateway.calls.set(0);
        flakyStepHandler.attempts.set(0);
    }

    @Test
    void initiateRunsStepsInOrderAndCompletesAfterAResumedRetry() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();
        authenticateAs(tenantId, initiatedBy);

        OffboardingJobEntity job = offboardingService.initiate(tenantId, userId, initiatedBy, "left the company");

        OffboardingJobEntity afterFirstRun = offboardingService.getJob(tenantId, job.getId());
        assertThat(afterFirstRun.getStatus()).isEqualTo(OffboardingJobStatus.FAILED);
        assertThat(afterFirstRun.getAttemptCount()).isEqualTo(1);
        assertThat(sessionRevocationGateway.calls.get()).isEqualTo(1);

        OffboardingJobEntity retried = offboardingService.retry(tenantId, job.getId());

        assertThat(retried.getStatus()).isEqualTo(OffboardingJobStatus.COMPLETED);
        assertThat(sessionRevocationGateway.calls.get()).isEqualTo(1);
        assertThat(flakyStepHandler.attempts.get()).isEqualTo(2);
    }

    @Test
    void retryOnACompletedJobIsANoOp() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID initiatedBy = UUID.randomUUID();
        authenticateAs(tenantId, initiatedBy);

        OffboardingJobEntity job = offboardingService.initiate(tenantId, userId, initiatedBy, "left the company");
        offboardingService.retry(tenantId, job.getId());
        OffboardingJobEntity completed = offboardingService.getJob(tenantId, job.getId());
        assertThat(completed.getStatus()).isEqualTo(OffboardingJobStatus.COMPLETED);

        int callsBeforeSecondRetry = sessionRevocationGateway.calls.get();
        OffboardingJobEntity secondRetry = offboardingService.retry(tenantId, job.getId());

        assertThat(secondRetry.getStatus()).isEqualTo(OffboardingJobStatus.COMPLETED);
        assertThat(sessionRevocationGateway.calls.get()).isEqualTo(callsBeforeSecondRetry);
    }
}
