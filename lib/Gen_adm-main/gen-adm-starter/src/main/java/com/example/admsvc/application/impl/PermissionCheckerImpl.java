package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionCheckerImpl implements PermissionChecker {

    private final UserRoleAssignmentService assignmentService;

    public PermissionCheckerImpl(UserRoleAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean has(GenAdmPrincipal principal, String permissionCode) {
        return assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId())
                .contains(permissionCode);
    }

    @Override
    @Transactional(readOnly = true)
    public void require(GenAdmPrincipal principal, String permissionCode) {
        if (!has(principal, permissionCode)) {
            throw new GenAdmForbiddenException("Missing required permission: " + permissionCode);
        }
    }
}
