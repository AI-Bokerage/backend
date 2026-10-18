// gen-tnt-starter/src/test/java/com/example/tnt_svc/web/ProvisioningControllerTest.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.config.GenTntProperties;
import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import com.example.tnt_svc.service.ProvisioningService;
import com.example.tnt_svc.web.security.InternalSecretFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProvisioningControllerTest {

    private final ProvisioningService provisioningService = mock(ProvisioningService.class);
    private final ProvisioningSagaOrchestrator orchestrator = mock(ProvisioningSagaOrchestrator.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GenTntProperties properties = new GenTntProperties();
        properties.setInternalSecret("test-secret");
        mockMvc = MockMvcBuilders.standaloneSetup(new ProvisioningController(provisioningService, orchestrator))
            .addFilter(new InternalSecretFilter(properties))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getJobRequiresSecret() throws Exception {
        mockMvc.perform(get("/api/v1/provisioning/jobs/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getJobReturnsJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProvisioningJob job = ProvisioningJob.builder().id(jobId).tenantId(UUID.randomUUID())
            .status(ProvisioningJobStatus.IN_PROGRESS).retryCount(0).maxRetries(3)
            .callbackToken("tok").context(java.util.Map.of()).build();
        when(provisioningService.getJob(jobId)).thenReturn(job);

        mockMvc.perform(get("/api/v1/provisioning/jobs/" + jobId).header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isOk());
    }

    @Test
    void listFailedReturnsList() throws Exception {
        when(provisioningService.listFailed()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/provisioning/failed").header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isOk());
    }

    @Test
    void retryTriggersManualRetry() throws Exception {
        UUID jobId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/provisioning/jobs/" + jobId + "/retry").header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isAccepted());

        verify(provisioningService, times(1)).manualRetry(jobId);
    }

    @Test
    void callbackRequiresInternalSecretToo() throws Exception {
        mockMvc.perform(post("/internal/provisioning/jobs/" + UUID.randomUUID() + "/steps/STEP_A/callback")
                .contentType("application/json")
                .content("{\"success\":true,\"token\":\"tok\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void callbackDelegatesToOrchestrator() throws Exception {
        UUID jobId = UUID.randomUUID();
        String body = new ObjectMapper().writeValueAsString(
            new com.example.tnt_svc.web.dto.StepCallbackRequest("tok", true, java.util.Map.of("k", "v"), null));

        mockMvc.perform(post("/internal/provisioning/jobs/" + jobId + "/steps/STEP_A/callback")
                .header("X-Internal-Secret", "test-secret")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk());

        verify(orchestrator, times(1)).handleCallback(eq(jobId), eq("STEP_A"), eq("tok"), eq(true), any(), eq((String) null));
    }

    @Test
    void callbackReturns409WhenLockContended() throws Exception {
        // Lock contention must not be swallowed as HTTP 200 — the webhook sender needs a
        // non-2xx so its at-least-once retry actually fires.
        UUID jobId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new com.example.tnt_svc.domain.exception.ProvisioningLockException(UUID.randomUUID()))
            .when(orchestrator).handleCallback(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean(), any(), org.mockito.ArgumentMatchers.nullable(String.class));
        String body = new ObjectMapper().writeValueAsString(
            new com.example.tnt_svc.web.dto.StepCallbackRequest("tok", true, java.util.Map.of(), null));

        mockMvc.perform(post("/internal/provisioning/jobs/" + jobId + "/steps/STEP_A/callback")
                .header("X-Internal-Secret", "test-secret")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isConflict());
    }
}
