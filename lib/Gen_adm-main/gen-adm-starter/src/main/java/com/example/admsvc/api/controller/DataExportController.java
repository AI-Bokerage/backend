package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.response.DataExportResponse;
import com.example.admsvc.application.service.DataExportService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exports")
public class DataExportController {

    private static final String MANAGE_EXPORTS = "adm:exports:manage";

    private final DataExportService dataExportService;
    private final PermissionChecker permissionChecker;

    public DataExportController(DataExportService dataExportService, PermissionChecker permissionChecker) {
        this.dataExportService = dataExportService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public DataExportResponse request(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        return DataExportResponse.from(dataExportService.request(principal.tenantId(), principal.userId()));
    }

    @GetMapping("/{id}")
    public DataExportResponse getStatus(@AuthenticationPrincipal GenAdmPrincipal principal,
                                        @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        return DataExportResponse.from(dataExportService.getStatus(principal.tenantId(), id));
    }

    @GetMapping
    public List<DataExportResponse> listExports(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        return dataExportService.listExports(principal.tenantId()).stream()
                .map(DataExportResponse::from)
                .toList();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<String> download(@AuthenticationPrincipal GenAdmPrincipal principal,
                                           @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        String snapshotJson = dataExportService.download(principal.tenantId(), id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(snapshotJson);
    }

    @PostMapping("/{id}/revoke")
    public DataExportResponse revoke(@AuthenticationPrincipal GenAdmPrincipal principal,
                                     @PathVariable UUID id) {
        permissionChecker.require(principal, MANAGE_EXPORTS);
        return DataExportResponse.from(
                dataExportService.revoke(principal.tenantId(), id, principal.userId()));
    }
}
