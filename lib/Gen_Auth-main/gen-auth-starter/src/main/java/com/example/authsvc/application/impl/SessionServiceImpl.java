package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.response.SessionResponse;
import com.example.authsvc.api.dto.response.SessionUserResponse;
import com.example.authsvc.application.service.SessionService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.port.PermissionCacheStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final AuthSessionJpaRepository      sessionRepo;
    private final AuthUserJpaRepository         userRepo;
    private final PermissionCacheStore          permissionCache;
    private final AuthCookieFactory             cookieFactory;

    @Override
    public SessionResponse getSession(AuthenticatedUser principal, HttpServletResponse response) {
        if (principal == null) {
            log.warn("session.unauthenticated");
            clearAuthCookies(response);
            throw new UnauthorizedException();
        }

        UUID    sessionId = UUID.fromString(principal.getSessionId());
        Instant now       = Instant.now();

        // ── Step 1: validate DB session ───────────────────────────────────────
        var session = sessionRepo.findByIdAndActiveTrue(sessionId)
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(now))
                .orElseGet(() -> {
                    log.warn("session.invalid sessionId={} userId={}", sessionId, principal.getUserId());
                    clearAuthCookies(response);
                    throw new UnauthorizedException();
                });

        log.debug("session.validated sessionId={} userId={}", sessionId, principal.getUserId());

        // ── Step 2: load email ────────────────────────────────────────────────
        String email = resolveEmail(session.getUserId());

        // ── Step 3: resolve permissions (Phase 4 — multi-role union) ─────────
        List<UUID> roleIds = principal.getRoleIds();
        Set<String> permissions = permissionCache.resolveAll(roleIds);
        log.debug("session.permissions resolved roleCount={} permissionCount={}",
                roleIds.size(), permissions.size());

        // ── Step 4: assemble response ─────────────────────────────────────────
        UUID firstRoleId = principal.getRoleId(); // compat scalar; null if no roles
        SessionUserResponse user = new SessionUserResponse(
                principal.getUserId(),
                principal.getTenantId(),
                email,
                principal.getUserType(),
                firstRoleId,  // backward compat
                roleIds       // full list
        );

        return new SessionResponse(user, permissions, principal.getExpiresAt());
    }

    private String resolveEmail(UUID userId) {
        return userRepo.findById(userId).map(u -> u.getEmail()).orElse(null);
    }

    private void clearAuthCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearAccessTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearRefreshTokenCookie().toString());
    }
}
