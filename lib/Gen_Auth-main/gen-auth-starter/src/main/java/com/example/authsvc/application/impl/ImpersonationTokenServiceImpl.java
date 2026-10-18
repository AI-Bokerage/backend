package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.application.service.ImpersonationTokenService;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Issues impersonation JWTs for {@code POST /internal/auth/impersonation-token}.
 * Gated by {@code app.super-admin.enabled=true}. Protected by the existing
 * {@link com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter}
 * (shared-secret header) via the {@code /internal/**} path prefix — no bespoke
 * per-request signature scheme, unlike the pre-trim version.
 *
 * <p>The caller is expected to have already resolved the impersonation role ID
 * (and, where available, the tenant slug) through its own authorization flow —
 * this service is a token factory: it records a session locally and signs a JWT,
 * nothing more.
 *
 * <p>Token structure:</p>
 * <pre>
 *   sub         → superAdminId
 *   tenant_id   → impersonated tenant UUID
 *   tenant_slug → impersonated tenant slug
 *   role_id     → impersonation role UUID
 *   user_type   → SUPER_ADMIN_IMPERSONATING
 *   session_id  → caller-supplied correlation ID
 *   exp         → now + jwt.impersonation-token.expiration-minutes (or 240 min if writeConsent)
 * </pre>
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.super-admin", name = "enabled", havingValue = "true")
public class ImpersonationTokenServiceImpl implements ImpersonationTokenService {

    private final JwtUtils                 jwtUtils;
    private final AuthSessionJpaRepository authSessionRepository;
    private final AuthUserJpaRepository    authUserRepository;
    private final TenantSlugResolver       tenantSlugResolver;
    private final UserDisplayNameResolver  userDisplayNameResolver;

    private final AuthEventPublisher authEventPublisher;

    @Value("${jwt.impersonation-token.expiration-minutes:60}")
    private int expirationMinutes;

    public ImpersonationTokenServiceImpl(
            JwtUtils jwtUtils,
            AuthSessionJpaRepository authSessionRepository,
            AuthUserJpaRepository authUserRepository,
            TenantSlugResolver tenantSlugResolver,
            UserDisplayNameResolver userDisplayNameResolver,
            @Autowired(required = false) AuthEventPublisher authEventPublisher) {
        this.jwtUtils = jwtUtils;
        this.authSessionRepository = authSessionRepository;
        this.authUserRepository = authUserRepository;
        this.tenantSlugResolver = tenantSlugResolver;
        this.userDisplayNameResolver = userDisplayNameResolver;
        this.authEventPublisher = authEventPublisher;
    }

    @Override
    @Transactional
    public ImpersonationTokenResponse issue(ImpersonationTokenRequest request) {
        Instant now = Instant.now();
        int ttlMinutes    = request.writeConsent() ? 240 : expirationMinutes;
        Instant expiresAt = now.plus(ttlMinutes, ChronoUnit.MINUTES);

        String tenantSlug = (request.tenantSlug() != null && !request.tenantSlug().isBlank())
                ? request.tenantSlug()
                : tenantSlugResolver.resolve(request.tenantId());

        log.debug("impersonation.slug.resolved tenantId={} slug={}", request.tenantId(), tenantSlug);

        AuthSessionEntity session = AuthSessionEntity.builder()
                .id(UUID.randomUUID())
                .userId(request.superAdminId())
                .tenantId(request.tenantId())
                .roleId(request.impersonationRoleId())
                .userType(UserType.SUPER_ADMIN_IMPERSONATING)
                .impersonation(true)
                .active(true)
                .expiresAt(expiresAt)
                .lastActivityAt(now)
                .build();

        authSessionRepository.save(session);

        // The acting user is the real super admin doing the impersonating, not the
        // impersonated tenant's own admin — username/email must identify who is
        // actually acting, which is what an audit trail needs.
        String username = userDisplayNameResolver.resolve(request.superAdminId());
        String email = authUserRepository.findById(request.superAdminId())
                .map(com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity::getEmail)
                .orElse(null);
        String tenantName = tenantSlugResolver.resolveName(request.tenantId());

        JwtClaims claims = new JwtClaims(
                request.superAdminId(),
                request.tenantId(),
                tenantSlug,
                List.of(request.impersonationRoleId()),
                UserType.SUPER_ADMIN_IMPERSONATING,
                now,
                expiresAt,
                request.sessionId(),
                null,
                username, email, tenantName
        );

        String accessToken = jwtUtils.generateAccessToken(claims);

        if (authEventPublisher != null) {
            authEventPublisher.publishImpersonationStarted(
                    request.superAdminId(), request.tenantId(), request.writeConsent() ? "write-consent" : null, expiresAt);
        }

        int expiresInSeconds = ttlMinutes * 60;
        log.info("impersonation.token.issued superAdminId={} tenantId={} sessionId={} expiresIn={}",
                request.superAdminId(), request.tenantId(), request.sessionId(), expiresInSeconds);
        return new ImpersonationTokenResponse(accessToken, expiresInSeconds, request.sessionId());
    }
}
