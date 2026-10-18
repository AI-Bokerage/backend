package com.example.gendemo;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Demo-only wrapper around the programmatic bootstrapTenant path — a real
 * host app calls UserRoleAssignmentService.bootstrapTenant(...) directly
 * from its own tenant-provisioning code, not over HTTP.
 */
@RestController
public class DemoBootstrapController {

    record BootstrapRequest(UUID tenantId, UUID userId, String roleName, Set<String> permissionCodes) {
    }

    private final UserRoleAssignmentService assignmentService;

    public DemoBootstrapController(UserRoleAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/internal/bootstrap")
    public void bootstrap(@RequestBody BootstrapRequest request) {
        assignmentService.bootstrapTenant(
                request.tenantId(), request.userId(), request.roleName(), request.permissionCodes());
    }
}
