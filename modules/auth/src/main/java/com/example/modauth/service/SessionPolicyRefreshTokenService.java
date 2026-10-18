package com.example.modauth.service;

import com.example.authsvc.api.dto.response.RefreshResult;
import com.example.authsvc.application.impl.RefreshTokenServiceImpl;
import com.example.authsvc.application.service.RefreshTokenService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.model.RefreshToken;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

/**
 * Flow 0 "Being logged out", the two server-side rules gen-auth-starter
 * doesn't cover, checked when the client trades its refresh token:
 *   - "You're inactive too long → logged out automatically": every refresh
 *     token records when it was issued (login or the previous refresh), so a
 *     refresh presented more than idle-timeout later means nobody was using
 *     the app in between. The frontend must refresh only on user activity
 *     (e.g. when an API call gets a 401), never on a background timer.
 *   - Flow 1 "Suspend → blocked": a suspended/cancelled brokerage can't refresh.
 * "Maximum session length" is already the starter's absolute expiry.
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class SessionPolicyRefreshTokenService implements RefreshTokenService {

    private final RefreshTokenServiceImpl delegate;
    private final RefreshTokenStore refreshTokenStore;
    private final TenantAccessService tenantAccessService;
    private final PlatformEventClient platformEvents;

    @Value("${modauth.session.idle-timeout-minutes:30}")
    private long idleTimeoutMinutes;

    @Override
    public RefreshResult refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        RefreshToken current = rawRefreshToken == null || rawRefreshToken.isBlank() ? null
                : refreshTokenStore.findByTokenHash(RefreshTokenHashUtil.hash(rawRefreshToken)).orElse(null);
        if (current != null) {
            if (tenantAccessService.isBlocked(current.getTenantId())) {
                delegate.logout(rawRefreshToken, ipAddress, userAgent);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Your brokerage's access is suspended. Contact your administrator.");
            }
            Instant issuedAt = current.getCreatedAt();
            if (issuedAt != null && Duration.between(issuedAt, Instant.now()).toMinutes() >= idleTimeoutMinutes) {
                delegate.logout(rawRefreshToken, ipAddress, userAgent);
                platformEvents.send("auth.logout.inactivity", current.getTenantId(), current.getUserId(), ipAddress,
                        "Signed out after " + idleTimeoutMinutes + " minutes of inactivity");
                log.info("session.idle_timeout userId={} sessionId={}", current.getUserId(), current.getSessionId());
                throw new UnauthorizedException("Signed out after inactivity — please log in again");
            }
        }
        return delegate.refresh(rawRefreshToken, ipAddress, userAgent);
    }

    @Override
    public void logout(String rawRefreshToken, String ipAddress, String userAgent) {
        delegate.logout(rawRefreshToken, ipAddress, userAgent);
    }
}
