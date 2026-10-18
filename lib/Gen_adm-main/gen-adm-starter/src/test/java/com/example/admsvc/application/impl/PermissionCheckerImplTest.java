package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCheckerImplTest {

    @Mock
    private UserRoleAssignmentService assignmentService;

    @InjectMocks
    private PermissionCheckerImpl permissionChecker;

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void hasReturnsTrueWhenPrincipalHoldsTheCode() {
        when(assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId()))
                .thenReturn(Set.of("users:read"));

        assertThat(permissionChecker.has(principal, "users:read")).isTrue();
    }

    @Test
    void hasReturnsFalseWhenPrincipalLacksTheCode() {
        when(assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId()))
                .thenReturn(Set.of());

        assertThat(permissionChecker.has(principal, "users:read")).isFalse();
    }

    @Test
    void requireThrowsForbiddenWhenPrincipalLacksTheCode() {
        when(assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId()))
                .thenReturn(Set.of());

        assertThatThrownBy(() -> permissionChecker.require(principal, "adm:roles:manage"))
                .isInstanceOf(GenAdmForbiddenException.class);
    }

    @Test
    void requireDoesNotThrowWhenPrincipalHoldsTheCode() {
        when(assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId()))
                .thenReturn(Set.of("adm:roles:manage"));

        permissionChecker.require(principal, "adm:roles:manage");
        // no exception = pass
    }
}
