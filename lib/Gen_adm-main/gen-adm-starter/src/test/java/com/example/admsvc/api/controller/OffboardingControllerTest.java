package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.OffboardingService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.OffboardingJobStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.OffboardingJobEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OffboardingControllerTest {

    private OffboardingService offboardingService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        offboardingService = mock(OffboardingService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OffboardingController(offboardingService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    @Test
    void initiatesAnOffboardingJobWhenPrincipalHasPermission() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq("adm:offboarding:manage"));
        UUID targetUserId = UUID.randomUUID();
        OffboardingJobEntity created = OffboardingJobEntity.builder()
                .id(UUID.randomUUID()).tenantId(principal.tenantId()).userId(targetUserId)
                .initiatedBy(principal.userId()).reason("left").status(OffboardingJobStatus.PENDING).build();
        when(offboardingService.initiate(eq(principal.tenantId()), eq(targetUserId), eq(principal.userId()), eq("left")))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/offboarding")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("userId", targetUserId.toString());
                            put("reason", "left");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void returns403WhenPrincipalLacksPermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: adm:offboarding:manage"))
                .when(permissionChecker).require(eq(principal), eq("adm:offboarding:manage"));

        mockMvc.perform(post("/api/v1/offboarding")
                        .contentType("application/json")
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\",\"reason\":\"left\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void returns404ForACrossTenantJobLookup() throws Exception {
        UUID jobId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq("adm:offboarding:manage"));
        when(offboardingService.getJob(eq(principal.tenantId()), eq(jobId)))
                .thenThrow(new GenAdmNotFoundException("Offboarding job not found: " + jobId));

        mockMvc.perform(get("/api/v1/offboarding/" + jobId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
