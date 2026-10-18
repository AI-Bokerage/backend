package com.example.admsvc.application.service;

import com.example.admsvc.domain.port.GenAdmPrincipal;

public interface PermissionChecker {

    boolean has(GenAdmPrincipal principal, String permissionCode);

    /**
     * Throws {@link com.example.admsvc.common.exception.GenAdmForbiddenException}
     * if {@code principal} does not hold {@code permissionCode}.
     */
    void require(GenAdmPrincipal principal, String permissionCode);
}
