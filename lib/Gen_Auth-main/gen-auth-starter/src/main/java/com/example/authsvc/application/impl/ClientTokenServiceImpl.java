package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ClientTokenRequest;
import com.example.authsvc.api.dto.response.ClientTokenResponse;
import com.example.authsvc.application.service.ClientTokenService;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Issues CLIENT-scoped JWTs for {@code POST /v1/client-token}. Gated by
 * {@code app.client-token.enabled=true}. Guarded by
 * {@link com.example.authsvc.infrastructure.security.filter.InternalHmacAuthFilter}
 * via {@code app.internal-hmac-auth.target-paths} — not by JWT, not by the
 * shared-secret filter.
 *
 * <p>The caller (e.g. CPT-SVC) is expected to have already verified the human
 * — this service is a token factory: it records a session locally and signs
 * a JWT, nothing more.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.client-token", name = "enabled", havingValue = "true")
public class ClientTokenServiceImpl implements ClientTokenService {

    private final JwtUtils                 jwtUtils;
    private final AuthSessionJpaRepository authSessionRepository;
    private final TenantSlugResolver       tenantSlugResolver;

    @Value("${jwt.client-token.expiration-minutes:15}")
    private int expirationMinutes;

    public ClientTokenServiceImpl(
            JwtUtils jwtUtils,
            AuthSessionJpaRepository authSessionRepository,
            TenantSlugResolver tenantSlugResolver) {
        this.jwtUtils = jwtUtils;
        this.authSessionRepository = authSessionRepository;
        this.tenantSlugResolver = tenantSlugResolver;
    }

    @Override
    @Transactional
    public ClientTokenResponse issue(ClientTokenRequest request) {
        Instant now       = Instant.now();
        Instant expiresAt = now.plus(expirationMinutes, ChronoUnit.MINUTES);

        String tenantSlug = (request.tenantSlug() != null && !request.tenantSlug().isBlank())
                ? request.tenantSlug()
                : tenantSlugResolver.resolve(request.tenantId());

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(UUID.randomUUID())
                .userId(request.clientUserId())
                .tenantId(request.tenantId())
                .roleId(request.roleId())
                .userType(UserType.CLIENT)
                .impersonation(false)
                .active(true)
                .expiresAt(expiresAt)
                .lastActivityAt(now)
                .build();

        authSessionRepository.save(session);

        JwtClaims claims = new JwtClaims(
                request.clientUserId(),
                request.tenantId(),
                tenantSlug,
                List.of(request.roleId()),
                UserType.CLIENT,
                now,
                expiresAt,
                request.sessionId(),
                null,
                null, null, null
        );

        String accessToken = jwtUtils.generateAccessToken(claims);
        int expiresInSeconds = expirationMinutes * 60;
        log.info("client.token.issued clientUserId={} tenantId={} sessionId={} expiresIn={}",
                request.clientUserId(), request.tenantId(), request.sessionId(), expiresInSeconds);
        return new ClientTokenResponse(accessToken, expiresInSeconds, request.sessionId());
    }
}
