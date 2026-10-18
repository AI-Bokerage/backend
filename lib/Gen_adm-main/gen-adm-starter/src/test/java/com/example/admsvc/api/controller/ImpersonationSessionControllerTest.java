package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.ImpersonationSessionService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.ImpersonationSessionStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.ImpersonationSessionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImpersonationSessionControllerTest {

    private static final String REQUEST_PERM = "adm:impersonation:request";
    private static final String MANAGE_PERM = "adm:impersonation:manage";

    private ImpersonationSessionService impersonationSessionService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        impersonationSessionService = mock(ImpersonationSessionService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ImpersonationSessionController(impersonationSessionService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    private ImpersonationSessionEntity session(ImpersonationSessionStatus status, UUID requestedBy, UUID target) {
        return ImpersonationSessionEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(principal.tenantId())
                .requestedByUserId(requestedBy)
                .targetUserId(target)
                .reason("support ticket #42")
                .status(status)
                .expiresAt(Instant.now().plus(30, ChronoUnit.MINUTES))
                .build();
    }

    @Test
    void createsASessionWhenPrincipalHasRequestPermission() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq(REQUEST_PERM));
        UUID targetUserId = UUID.randomUUID();
        ImpersonationSessionEntity created = session(ImpersonationSessionStatus.PENDING_CONSENT,
                principal.userId(), targetUserId);
        when(impersonationSessionService.create(eq(principal.tenantId()), eq(principal.userId()),
                eq(targetUserId), eq("support ticket #42"), eq(30))).thenReturn(created);

        mockMvc.perform(post("/api/v1/impersonation-requests")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new HashMap<>() {{
                            put("targetUserId", targetUserId.toString());
                            put("reason", "support ticket #42");
                            put("ttlMinutes", 30);
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_CONSENT"));
    }

    @Test
    void returns403WhenPrincipalLacksRequestPermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + REQUEST_PERM))
                .when(permissionChecker).require(eq(principal), eq(REQUEST_PERM));

        mockMvc.perform(post("/api/v1/impersonation-requests")
                        .contentType("application/json")
                        .content("{\"targetUserId\":\"" + UUID.randomUUID()
                                + "\",\"reason\":\"support ticket #42\",\"ttlMinutes\":30}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listRequiresManagePermission() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(impersonationSessionService.listActionable(principal.tenantId())).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/impersonation-requests"))
                .andExpect(status().isOk());
        verify(permissionChecker).require(principal, MANAGE_PERM);
    }

    @Test
    void approveReturns404ForACrossTenantSession() throws Exception {
        UUID sessionId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));
        when(impersonationSessionService.approve(eq(principal.tenantId()), eq(sessionId), eq(principal.userId())))
                .thenThrow(new GenAdmNotFoundException("Impersonation session not found: " + sessionId));

        mockMvc.perform(post("/api/v1/impersonation-requests/" + sessionId + "/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void endPassesPrivilegedFalseWhenPrincipalLacksManagePermission() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(permissionChecker.has(principal, MANAGE_PERM)).thenReturn(false);
        ImpersonationSessionEntity ended = session(ImpersonationSessionStatus.ENDED, principal.userId(), UUID.randomUUID());
        when(impersonationSessionService.end(principal.tenantId(), sessionId, principal.userId(), false))
                .thenReturn(ended);

        mockMvc.perform(post("/api/v1/impersonation-requests/" + sessionId + "/end"))
                .andExpect(status().isOk());
        verify(impersonationSessionService).end(principal.tenantId(), sessionId, principal.userId(), false);
    }

    @Test
    void endPassesPrivilegedTrueWhenPrincipalHasManagePermission() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(permissionChecker.has(principal, MANAGE_PERM)).thenReturn(true);
        ImpersonationSessionEntity ended = session(ImpersonationSessionStatus.ENDED, UUID.randomUUID(), UUID.randomUUID());
        when(impersonationSessionService.end(principal.tenantId(), sessionId, principal.userId(), true))
                .thenReturn(ended);

        mockMvc.perform(post("/api/v1/impersonation-requests/" + sessionId + "/end"))
                .andExpect(status().isOk());
        verify(impersonationSessionService).end(principal.tenantId(), sessionId, principal.userId(), true);
    }
}
