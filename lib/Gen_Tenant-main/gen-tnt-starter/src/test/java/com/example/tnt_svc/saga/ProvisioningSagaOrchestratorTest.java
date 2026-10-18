// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningSagaOrchestratorTest.java
package com.example.tnt_svc.saga;

import com.example.tnt_svc.AbstractIntegrationTest;
import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.domain.ProvisioningStep;
import com.example.tnt_svc.domain.ProvisioningStepStatus;
import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.domain.exception.ProvisioningLockException;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import com.example.tnt_svc.persistence.ProvisioningStepRepository;
import com.example.tnt_svc.persistence.TenantRepository;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProvisioningSagaOrchestratorTest extends AbstractIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedis(DynamicPropertyRegistry registry) {
        REDIS.start();
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("gentnt.provisioning.max-retries", () -> "3");
        registry.add("gentnt.provisioning.timeout-minutes", () -> "10");
    }

    @Autowired
    private ProvisioningSagaOrchestrator orchestrator;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ProvisioningJobRepository jobRepository;
    @Autowired
    private ProvisioningStepRepository stepRepository;
    @Autowired
    private ProvisioningStepsProperties stepsProperties;
    @Autowired
    private ProvisioningLockService lockService;

    private Tenant tenant;

    @BeforeEach
    void setUpTenant() {
        tenant = tenantRepository.save(Tenant.builder()
            .slug("orch-test-" + UUID.randomUUID())
            .name("Orchestrator Test")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build());
    }

    @Test
    void allSyncStepsSucceedActivatesTenant() {
        wireMock.stubFor(post(urlEqualTo("/sync-step")).willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json").withBody("{}")));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ONLY_STEP", wireMock.baseUrl() + "/sync-step", StepMode.SYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());

        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(ProvisioningJobStatus.COMPLETED);
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void twoConsecutiveSyncStepsBothCompleteInOneChain() {
        wireMock.stubFor(post(urlEqualTo("/sync-step-a")).willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json").withBody("{}")));
        wireMock.stubFor(post(urlEqualTo("/sync-step-b")).willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json").withBody("{}")));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("SYNC_STEP_A", wireMock.baseUrl() + "/sync-step-a", StepMode.SYNC, true, null),
            new ProvisioningStepDefinition("SYNC_STEP_B", wireMock.baseUrl() + "/sync-step-b", StepMode.SYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());

        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(ProvisioningJobStatus.COMPLETED);

        List<ProvisioningStep> steps = stepRepository.findByJobIdOrderByStepOrderAsc(jobId);
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).getStatus()).isEqualTo(ProvisioningStepStatus.COMPLETED);
        assertThat(steps.get(1).getStatus()).isEqualTo(ProvisioningStepStatus.COMPLETED);

        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void syncStepFailureMarksJobFailed() {
        wireMock.stubFor(post(urlEqualTo("/fail-step")).willReturn(aResponse().withStatus(500)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ONLY_STEP", wireMock.baseUrl() + "/fail-step", StepMode.SYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.FAILED);
    }

    @Test
    void asyncStepAdvancesOnSuccessfulCallback() {
        wireMock.stubFor(post(urlEqualTo("/async-step")).willReturn(aResponse().withStatus(202)));
        wireMock.stubFor(post(urlEqualTo("/sync-step-2")).willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json").withBody("{}")));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-step", StepMode.ASYNC, true, null),
            new ProvisioningStepDefinition("SYNC_STEP_2", wireMock.baseUrl() + "/sync-step-2", StepMode.SYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        ProvisioningJob afterStart = jobRepository.findById(jobId).orElseThrow();
        assertThat(afterStart.getStatus()).isEqualTo(ProvisioningJobStatus.IN_PROGRESS);

        orchestrator.handleCallback(jobId, "ASYNC_STEP", afterStart.getCallbackToken(), true, Map.of("k", "v"), null);

        ProvisioningJob afterCallback = jobRepository.findById(jobId).orElseThrow();
        assertThat(afterCallback.getStatus()).isEqualTo(ProvisioningJobStatus.COMPLETED);
        assertThat(afterCallback.getContext()).containsEntry("k", "v");
    }

    @Test
    void callbackWithWrongTokenIsRejected() {
        wireMock.stubFor(post(urlEqualTo("/async-step2")).willReturn(aResponse().withStatus(202)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-step2", StepMode.ASYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());

        orchestrator.handleCallback(jobId, "ASYNC_STEP", "wrong-token", true, Map.of(), null);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.IN_PROGRESS);
    }

    @Test
    void retryProvisioningMarksJobDeadWhenRetriesExhausted() {
        wireMock.stubFor(post(urlEqualTo("/always-fail")).willReturn(aResponse().withStatus(500)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ONLY_STEP", wireMock.baseUrl() + "/always-fail", StepMode.SYNC, true, null)
        ));
        stepsProperties.setMaxRetries(0);

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.FAILED);

        orchestrator.retryProvisioning(jobId);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.DEAD);
    }

    @Test
    void retryProvisioningIsNoOpWhenJobNotFailed() {
        // Residual finding: RetryRecoveryScheduler snapshots FAILED jobs and calls
        // retryProvisioning for each with no re-check at call time. If a manual retry (or another
        // scheduler tick) already moved the job off FAILED (e.g. to IN_PROGRESS) between the
        // snapshot and this call, retryProvisioning must be a no-op — it must NOT markDead() a
        // job that isn't actually FAILED.
        wireMock.stubFor(post(urlEqualTo("/async-not-failed")).willReturn(aResponse().withStatus(202)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-not-failed", StepMode.ASYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.IN_PROGRESS);

        orchestrator.retryProvisioning(jobId);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.IN_PROGRESS);
    }

    @Test
    void callbackForDeadJobIsRejectedAndTenantNotActivated() {
        // Finding 1: a late/duplicated async callback for a job already marked DEAD (compensated)
        // must not resurrect it and activate a rolled-back tenant.
        wireMock.stubFor(post(urlEqualTo("/async-dead")).willReturn(aResponse().withStatus(202)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-dead", StepMode.ASYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();

        orchestrator.handleTimeout(jobId); // marks the job DEAD
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.DEAD);

        orchestrator.handleCallback(jobId, "ASYNC_STEP", job.getCallbackToken(), true, Map.of(), null);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.DEAD);
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getStatus()).isEqualTo(TenantStatus.PROVISIONING);
    }

    @Test
    void callbackUnderLockContentionThrowsSoCallerRetries() {
        // Finding 3: when the per-tenant lock is held, the callback must surface (not silently
        // return) so the controller returns non-2xx and the sender retries.
        wireMock.stubFor(post(urlEqualTo("/async-contended")).willReturn(aResponse().withStatus(202)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-contended", StepMode.ASYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();

        Optional<String> held = lockService.tryLock(tenant.getId(), Duration.ofMinutes(5));
        assertThat(held).isPresent();
        try {
            assertThatThrownBy(() ->
                orchestrator.handleCallback(jobId, "ASYNC_STEP", job.getCallbackToken(), true, Map.of(), null))
                .isInstanceOf(ProvisioningLockException.class);
        } finally {
            lockService.unlock(tenant.getId(), held.get());
        }
    }

    @Test
    void handleTimeoutSkipsWhenLockAlreadyHeld() {
        // C2: handleTimeout must not mutate state (compensate + markDead) while a driveNextStep
        // or handleCallback is genuinely in progress and holding the per-tenant lock — otherwise
        // a real async callback landing concurrently can race it to a corrupted end state.
        wireMock.stubFor(post(urlEqualTo("/async-timeout-race")).willReturn(aResponse().withStatus(202)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-timeout-race", StepMode.ASYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.IN_PROGRESS);

        Optional<String> held = lockService.tryLock(tenant.getId(), Duration.ofMinutes(5));
        assertThat(held).isPresent();
        try {
            orchestrator.handleTimeout(jobId);
        } finally {
            lockService.unlock(tenant.getId(), held.get());
        }

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.IN_PROGRESS);
    }

    @Test
    void retryProvisioningExtendsExpiresAt() {
        // Finding 4: retry must push expiresAt out so the timeout scheduler doesn't immediately reap it.
        wireMock.stubFor(post(urlEqualTo("/retry-fail")).willReturn(aResponse().withStatus(500)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ONLY_STEP", wireMock.baseUrl() + "/retry-fail", StepMode.SYNC, true, null)
        ));
        stepsProperties.setMaxRetries(3);

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        ProvisioningJob failed = jobRepository.findById(jobId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ProvisioningJobStatus.FAILED);

        // Force the window into the past to prove retry recomputes it.
        failed.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
        jobRepository.save(failed);

        Instant beforeRetry = Instant.now();
        orchestrator.retryProvisioning(jobId);

        assertThat(jobRepository.findById(jobId).orElseThrow().getExpiresAt()).isAfter(beforeRetry);
    }

    @Test
    void completeJobIsNoOpWhenTenantAlreadyCancelled() {
        // Finding 6: if an operator cancels a still-PROVISIONING tenant, a later step completion
        // must not throw InvalidTenantStateTransitionException — cancellation wins.
        wireMock.stubFor(post(urlEqualTo("/async-cancel")).willReturn(aResponse().withStatus(202)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-cancel", StepMode.ASYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();

        Tenant cancelled = tenantRepository.findById(tenant.getId()).orElseThrow();
        cancelled.cancel();
        tenantRepository.save(cancelled);

        assertThatCode(() ->
            orchestrator.handleCallback(jobId, "ASYNC_STEP", job.getCallbackToken(), true, Map.of(), null))
            .doesNotThrowAnyException();

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.COMPLETED);
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getStatus()).isEqualTo(TenantStatus.CANCELLED);
    }
}
