package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.application.service.SuperAdminLoginService;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.PlatformSuperAdminJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Authenticates the platform super admin against the {@code platform_super_admin}
 * table. Gated by {@code app.super-admin.enabled=true}.
 *
 * <p>After a successful lookup the entity is mapped to a synthetic
 * {@link AuthUserEntity} and the shared {@link LoginExecutionService} executes
 * the remaining critical path (password verification → session → JWT → Redis →
 * async side-effects) — identical to regular tenant-user login.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SuperAdminLoginServiceImpl implements SuperAdminLoginService {

    private final PlatformSuperAdminJpaRepository superAdminRepo;
    private final LockoutService                  lockoutService;
    private final LoginExecutionService           loginExecutor;

    @Override
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail().toLowerCase().strip();
        long loginStart = System.currentTimeMillis();
        log.info("superadmin.login.started email={} ip={}", email, ipAddress);

        lockoutService.checkLockout(email, ipAddress);

        AuthUserEntity user = superAdminRepo.findByEmailAndActiveTrue(email)
                .map(sa -> AuthUserEntity.builder()
                        .id(sa.getId())
                        .tenantId(TenantConstants.PLATFORM_TENANT_ID)
                        .email(sa.getEmail())
                        .passwordHash(sa.getPasswordHash())
                        .userType(UserType.SUPER_ADMIN)
                        .active(true)
                        .build())
                .orElse(null);

        if (user == null) {
            log.warn("superadmin.login.user_not_found email={} ip={}", email, ipAddress);
            loginExecutor.handleFailure(null, null, email, ipAddress, userAgent, "INVALID_CREDENTIALS");
            throw new InvalidCredentialsException();
        }

        MDC.put("userId",   user.getId().toString());
        MDC.put("tenantId", user.getTenantId().toString());
        try {
            return loginExecutor.executeLogin(user, request, ipAddress, userAgent, loginStart);
        } finally {
            MDC.remove("userId");
            MDC.remove("tenantId");
        }
    }
}
