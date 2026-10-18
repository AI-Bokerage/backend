package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.application.service.LoginService;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.common.exception.InvalidCredentialsException;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Authenticates regular tenant users from the {@code auth_users} table.
 *
 * <p>User lookup is delegated to {@link AuthUserJpaRepository}; all subsequent
 * steps (password verification, session, JWT, Redis, async side-effects) are
 * handled by the shared {@link LoginExecutionService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final AuthUserJpaRepository userRepo;
    private final LockoutService        lockoutService;
    private final LoginExecutionService loginExecutor;

    @Override
    public LoginResult login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail().toLowerCase().strip();
        long loginStart = System.currentTimeMillis();
        log.info("login.started email={} ip={}", email, ipAddress);

        // ── 1. Lockout guard — sync, security-critical ────────────────────────
        lockoutService.checkLockout(email, ipAddress);

        // ── 2. User lookup — read-only, outside any transaction ───────────────
        long t0 = System.currentTimeMillis();
        AuthUserEntity user = userRepo.findByEmailAndActiveTrue(email).orElse(null);
        log.info("perf.login.user_lookup.ms={}", System.currentTimeMillis() - t0);

        if (user == null) {
            log.warn("login.user_not_found email={} ip={}", email, ipAddress);
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
