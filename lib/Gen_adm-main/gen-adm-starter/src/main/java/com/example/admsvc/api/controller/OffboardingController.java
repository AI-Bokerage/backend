package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.InitiateOffboardingRequest;
import com.example.admsvc.api.dto.response.OffboardingJobResponse;
import com.example.admsvc.application.service.OffboardingService;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/offboarding")
public class OffboardingController {

    private static final String MANAGE_OFFBOARDING = "adm:offboarding:manage";

    private final OffboardingService offboardingService;
    private final PermissionChecker permissionChecker;

    public OffboardingController(OffboardingService offboardingService, PermissionChecker permissionChecker) {
        this.offboardingService = offboardingService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public OffboardingJobResponse initiate(@AuthenticationPrincipal GenAdmPrincipal principal,
                                            @Valid @RequestBody InitiateOffboardingRequest request) {
        permissionChecker.require(principal, MANAGE_OFFBOARDING);
        return OffboardingJobResponse.from(offboardingService.initiate(
                principal.tenantId(), request.userId(), principal.userId(), request.reason()));
    }

    @GetMapping("/{jobId}")
    public OffboardingJobResponse status(@AuthenticationPrincipal GenAdmPrincipal principal,
                                          @PathVariable UUID jobId) {
        permissionChecker.require(principal, MANAGE_OFFBOARDING);
        return OffboardingJobResponse.from(offboardingService.getJob(principal.tenantId(), jobId));
    }

    @PostMapping("/{jobId}/retry")
    public OffboardingJobResponse retry(@AuthenticationPrincipal GenAdmPrincipal principal,
                                         @PathVariable UUID jobId) {
        permissionChecker.require(principal, MANAGE_OFFBOARDING);
        return OffboardingJobResponse.from(offboardingService.retry(principal.tenantId(), jobId));
    }
}
