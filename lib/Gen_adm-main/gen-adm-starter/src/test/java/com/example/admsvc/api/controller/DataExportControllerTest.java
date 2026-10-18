package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.DataExportService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.enums.DataExportStatus;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.DataExportEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DataExportControllerTest {

    private static final String MANAGE_PERM = "adm:exports:manage";

    private DataExportService dataExportService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        dataExportService = mock(DataExportService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DataExportController(dataExportService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    private DataExportEntity export(DataExportStatus status) {
        return DataExportEntity.builder()
                .id(UUID.randomUUID()).tenantId(principal.tenantId())
                .requestedByUserId(principal.userId())
                .status(status)
                .snapshotJson("{\"tenantId\":\"" + principal.tenantId() + "\"}")
                .recordCount(2L)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void requestRequiresManagePermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + MANAGE_PERM))
                .when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));

        mockMvc.perform(post("/api/v1/exports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void requestReturnsTheCompletedExport() throws Exception {
        DataExportEntity created = export(DataExportStatus.COMPLETED);
        when(dataExportService.request(principal.tenantId(), principal.userId())).thenReturn(created);

        mockMvc.perform(post("/api/v1/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.recordCount").value(2));
        verify(permissionChecker).require(principal, MANAGE_PERM);
    }

    @Test
    void listExportsRequiresManagePermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: " + MANAGE_PERM))
                .when(permissionChecker).require(eq(principal), eq(MANAGE_PERM));

        mockMvc.perform(get("/api/v1/exports"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void listExportsReturnsEveryExportForTheTenant() throws Exception {
        when(dataExportService.listExports(principal.tenantId()))
                .thenReturn(List.of(export(DataExportStatus.COMPLETED)));

        mockMvc.perform(get("/api/v1/exports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"));
    }

    @Test
    void getStatusReturns404ForACrossTenantExport() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(dataExportService.getStatus(principal.tenantId(), exportId))
                .thenThrow(new GenAdmNotFoundException("Export not found: " + exportId));

        mockMvc.perform(get("/api/v1/exports/" + exportId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void downloadReturnsTheRawSnapshotJson() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(dataExportService.download(principal.tenantId(), exportId))
                .thenReturn("{\"tenantId\":\"" + principal.tenantId() + "\"}");

        mockMvc.perform(get("/api/v1/exports/" + exportId + "/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.tenantId").value(principal.tenantId().toString()));
    }

    @Test
    void revokeRequiresManagePermissionAndPassesRevokedBy() throws Exception {
        UUID exportId = UUID.randomUUID();
        when(dataExportService.revoke(principal.tenantId(), exportId, principal.userId()))
                .thenReturn(export(DataExportStatus.REVOKED));

        mockMvc.perform(post("/api/v1/exports/" + exportId + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
        verify(dataExportService).revoke(principal.tenantId(), exportId, principal.userId());
        verify(permissionChecker).require(principal, MANAGE_PERM);
    }
}
