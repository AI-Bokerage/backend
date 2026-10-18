package com.example.modauth.service;

import com.example.authsvc.api.dto.request.AuditLogRequest;
import com.example.authsvc.application.impl.AuditLogServiceImpl;
import com.example.authsvc.application.impl.RefreshTokenServiceImpl;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.model.RefreshToken;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.refresh.RefreshTokenHashUtil;
import com.example.modauth.domain.Role;
import com.example.modauth.entity.ModAuthUserRoleEntity;
import com.example.modauth.entity.TenantAccessEntity;
import com.example.modauth.repository.ModAuthUserRoleJpaRepository;
import com.example.modauth.repository.TenantAccessJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Flow 0 "inactive too long → logged out", Flow 1 "suspend → blocked", Flow 1 activity log forwarding. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionAndTenantAccessTest {

    @Mock private RefreshTokenServiceImpl starterRefresh;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private TenantAccessJpaRepository accessRepo;
    @Mock private ModAuthUserRoleJpaRepository roleRepo;
    @Mock private AuthRefreshTokenJpaRepository refreshTokenRepo;
    @Mock private AuthSessionJpaRepository sessionRepo;
    @Mock private PlatformEventClient platformEvents;
    @Mock private AuditLogServiceImpl starterAudit;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private TenantAccessService tenantAccess;
    private SessionPolicyRefreshTokenService refresh;

    @BeforeEach
    void setUp() {
        tenantAccess = new TenantAccessService(accessRepo, roleRepo, refreshTokenRepo, sessionRepo);
        refresh = new SessionPolicyRefreshTokenService(starterRefresh, refreshTokenStore, tenantAccess, platformEvents);
        ReflectionTestUtils.setField(refresh, "idleTimeoutMinutes", 30L);
        when(accessRepo.findById(tenantId)).thenReturn(Optional.empty());
    }

    private void tokenIssuedMinutesAgo(long minutes) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTenantId(tenantId);
        token.setCreatedAt(Instant.now().minus(minutes, ChronoUnit.MINUTES));
        when(refreshTokenStore.findByTokenHash(RefreshTokenHashUtil.hash("raw"))).thenReturn(Optional.of(token));
    }

    @Test
    void anActiveUserRefreshesNormally() {
        tokenIssuedMinutesAgo(10);
        refresh.refresh("raw", "ip", "ua");
        verify(starterRefresh).refresh("raw", "ip", "ua");
        verify(starterRefresh, never()).logout(anyString(), anyString(), anyString());
    }

    @Test
    void aRefreshAfterTheIdleWindowSignsTheSessionOut() {
        tokenIssuedMinutesAgo(45);
        assertThatThrownBy(() -> refresh.refresh("raw", "ip", "ua"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("inactivity");
        verify(starterRefresh).logout("raw", "ip", "ua");
        verify(starterRefresh, never()).refresh(anyString(), anyString(), anyString());
        verify(platformEvents).send(eq("auth.logout.inactivity"), eq(tenantId), eq(userId), eq("ip"), anyString());
    }

    @Test
    void aSuspendedBrokerageCannotRefresh() {
        tokenIssuedMinutesAgo(1);
        when(accessRepo.findById(tenantId)).thenReturn(Optional.of(
                TenantAccessEntity.builder().tenantId(tenantId).blocked(true).status("SUSPENDED").build()));
        assertThatThrownBy(() -> refresh.refresh("raw", "ip", "ua"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("suspended");
        verify(starterRefresh).logout("raw", "ip", "ua");
    }

    @Test
    void blockingABrokerageSignsEveryoneOut_reactivatingDoesNot() {
        UUID a = UUID.randomUUID();
        when(roleRepo.findByTenantId(tenantId)).thenReturn(List.of(
                ModAuthUserRoleEntity.builder().userId(a).tenantId(tenantId).role(Role.BROKER).acceptedTermsVersion(1).build()));

        tenantAccess.setAccess(tenantId, true, "SUSPENDED");
        verify(refreshTokenRepo).revokeAllByUserId(eq(a), any());
        verify(sessionRepo).deactivateAllByUserId(eq(a), any());

        tenantAccess.setAccess(tenantId, false, "ACTIVE");
        verify(refreshTokenRepo).revokeAllByUserId(eq(a), any()); // still just the one call
    }

    @Test
    void signInEventsAreForwardedToThePlatformActivityLog_otherAuditRowsStayLocal() {
        var audit = new ForwardingAuditLogService(starterAudit, platformEvents);
        audit.log(new AuditLogRequest(tenantId, userId, "LOGIN_SUCCESS", "ip", "ua", null));
        audit.log(new AuditLogRequest(tenantId, userId, "TOKEN_REFRESHED", "ip", "ua", null));

        verify(platformEvents).send("auth.login.success", tenantId, userId, "ip", null);
        verify(platformEvents, never()).send(eq("TOKEN_REFRESHED"), any(), any(), any(), any());
        assertThat(org.mockito.Mockito.mockingDetails(starterAudit).getInvocations()).hasSize(2);
    }
}
