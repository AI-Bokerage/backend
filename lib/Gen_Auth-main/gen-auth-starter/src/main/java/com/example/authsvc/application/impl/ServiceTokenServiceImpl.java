package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.response.ServiceTokenResponse;
import com.example.authsvc.application.service.ServiceTokenService;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Issues stateless service-account JWTs for {@code POST /internal/service-token}.
 * Gated by {@code app.super-admin.enabled=true} — mints a {@code SUPER_ADMIN}-scoped
 * token, so it rides the same feature flag as {@link ImpersonationTokenServiceImpl}.
 * No session is persisted: purely a stateless mint for trusted internal callers.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
public class ServiceTokenServiceImpl implements ServiceTokenService {

    private final JwtUtils jwtUtils;

    @Value("${jwt.service-token.expiration-minutes:5}")
    private int expirationMinutes;

    public ServiceTokenServiceImpl(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public ServiceTokenResponse issue(String callerService) {
        Instant now       = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        JwtClaims claims = new JwtClaims(
                TenantConstants.SERVICE_ACCOUNT_ID,
                TenantConstants.PLATFORM_TENANT_ID,
                "platform",
                List.of(),
                UserType.SUPER_ADMIN,
                now,
                expiresAt,
                "svc:" + callerService,
                null,
                null, null, null
        );

        String accessToken = jwtUtils.generateAccessToken(claims);
        int expiresInSeconds = expirationMinutes * 60;
        log.info("service.token.issued caller={} expiresIn={}", callerService, expiresInSeconds);
        return new ServiceTokenResponse(accessToken, expiresInSeconds);
    }
}
