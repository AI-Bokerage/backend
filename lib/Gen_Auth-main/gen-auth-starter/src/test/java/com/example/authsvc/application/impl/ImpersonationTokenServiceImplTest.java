package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.ImpersonationTokenRequest;
import com.example.authsvc.api.dto.response.ImpersonationTokenResponse;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import com.example.authsvc.infrastructure.persistence.entity.AuthSessionEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImpersonationTokenServiceImplTest {

    @Mock private JwtUtils                 jwtUtils;
    @Mock private AuthSessionJpaRepository authSessionRepository;
    @Mock private TenantSlugResolver       tenantSlugResolver;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository authUserRepository;
    @Mock private com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver userDisplayNameResolver;

    @Test
    void issue_readOnly_usesConfiguredTtlAndResolvesSlugViaFallback() throws Exception {
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, authUserRepository, tenantSlugResolver, userDisplayNameResolver, authEventPublisher);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, null, roleId, "session-abc", false);

        ImpersonationTokenResponse response = service.issue(request);

        assertThat(response.accessToken()).isEqualTo("signed-jwt");
        assertThat(response.expiresIn()).isEqualTo(60 * 60);
        assertThat(response.sessionId()).isEqualTo("session-abc");

        ArgumentCaptor<AuthSessionEntity> captor = ArgumentCaptor.forClass(AuthSessionEntity.class);
        verify(authSessionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserType()).isEqualTo(UserType.SUPER_ADMIN_IMPERSONATING);
        assertThat(captor.getValue().isImpersonation()).isTrue();
    }

    @Test
    void issue_writeConsent_usesExtendedFourHourTtl() throws Exception {
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, authUserRepository, tenantSlugResolver, userDisplayNameResolver, authEventPublisher);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, null, roleId, "session-abc", true);

        ImpersonationTokenResponse response = service.issue(request);

        assertThat(response.expiresIn()).isEqualTo(240 * 60);
    }

    @Test
    void issue_tenantSlugProvided_skipsResolverFallback() throws Exception {
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, authUserRepository, tenantSlugResolver, userDisplayNameResolver, authEventPublisher);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, "acme", roleId, "session-abc", false);

        service.issue(request);

        verify(tenantSlugResolver, org.mockito.Mockito.never()).resolve(any());
    }

    @Test
    void issue_publishesImpersonationStartedEvent() throws Exception {
        ImpersonationTokenServiceImpl service =
                new ImpersonationTokenServiceImpl(jwtUtils, authSessionRepository, authUserRepository, tenantSlugResolver, userDisplayNameResolver, authEventPublisher);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, "acme", roleId, "session-abc", false);

        service.issue(request);

        verify(authEventPublisher).publishImpersonationStarted(
                eq(superAdminId), eq(tenantId), any(), any());
    }

    @Test
    void issue_populatesUsernameUserEmailTenantNameFromActingSuperAdmin() throws Exception {
        ImpersonationTokenServiceImpl service = new ImpersonationTokenServiceImpl(
                jwtUtils, authSessionRepository, authUserRepository, tenantSlugResolver,
                userDisplayNameResolver, authEventPublisher);
        setExpirationMinutes(service, 60);

        UUID superAdminId = UUID.randomUUID();
        UUID tenantId     = UUID.randomUUID();
        UUID roleId       = UUID.randomUUID();

        com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity superAdmin =
                com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity.builder()
                        .id(superAdminId)
                        .email("admin@example.com")
                        .userType(UserType.SUPER_ADMIN)
                        .active(true)
                        .build();

        when(authUserRepository.findById(superAdminId)).thenReturn(java.util.Optional.of(superAdmin));
        when(userDisplayNameResolver.resolve(superAdminId)).thenReturn("Admin Name");
        when(tenantSlugResolver.resolveName(tenantId)).thenReturn("Acme Corp");
        when(jwtUtils.generateAccessToken(any(JwtClaims.class))).thenReturn("signed-jwt");

        ImpersonationTokenRequest request = new ImpersonationTokenRequest(
                superAdminId, tenantId, "acme", roleId, "session-abc", false);

        service.issue(request);

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateAccessToken(claimsCaptor.capture());
        assertThat(claimsCaptor.getValue().username()).isEqualTo("Admin Name");
        assertThat(claimsCaptor.getValue().userEmail()).isEqualTo("admin@example.com");
        assertThat(claimsCaptor.getValue().tenantName()).isEqualTo("Acme Corp");
    }

    private static void setExpirationMinutes(ImpersonationTokenServiceImpl service, int value) throws Exception {
        var field = ImpersonationTokenServiceImpl.class.getDeclaredField("expirationMinutes");
        field.setAccessible(true);
        field.set(service, value);
    }
}
