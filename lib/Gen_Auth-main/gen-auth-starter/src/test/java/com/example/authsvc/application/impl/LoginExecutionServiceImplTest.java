package com.example.authsvc.application.impl;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.service.AuditLogService;
import com.example.authsvc.application.service.LoginAttemptService;
import com.example.authsvc.application.service.LockoutService;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.JwtClaims;
import com.example.authsvc.domain.model.TokenPair;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthRefreshTokenJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthSessionJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserRoleJpaRepository;
import com.example.authsvc.infrastructure.security.jwt.TenantSlugResolver;
import com.example.authsvc.infrastructure.security.jwt.util.JwtUtils;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import com.example.authsvc.infrastructure.messaging.producer.AuthEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginExecutionServiceImplTest {

    @Mock private AuthSessionJpaRepository sessionRepo;
    @Mock private AuthRefreshTokenJpaRepository refreshTokenAuditRepo;
    @Mock private AuthUserRoleJpaRepository authUserRoleRepository;
    @Mock private RefreshTokenStore refreshTokenStore;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private LockoutService lockoutService;
    @Mock private AuditLogService auditLogService;
    @Mock private JwtUtils jwtUtils;
    @Mock private PasswordHasher passwordHasher;
    @Mock private Executor asyncExecutor;
    @Mock private PlatformTransactionManager txManager;
    @Mock private TenantSlugResolver tenantSlugResolver;
    @Mock private AuthEventPublisher authEventPublisher;
    @Mock private com.example.authsvc.application.service.MfaLoginGate mfaLoginGate;
    @Mock private com.example.authsvc.infrastructure.security.jwt.UserDisplayNameResolver userDisplayNameResolver;

    private LoginExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LoginExecutionServiceImpl(
                sessionRepo,
                refreshTokenAuditRepo,
                authUserRoleRepository,
                refreshTokenStore,
                loginAttemptService,
                lockoutService,
                auditLogService,
                jwtUtils,
                passwordHasher,
                asyncExecutor,
                txManager,
                tenantSlugResolver,
                authEventPublisher,
                mfaLoginGate,
                userDisplayNameResolver
        );
    }

    @Test
    void tenantUserWithNoRoleAssignmentsCanStillLogIn() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("newuser@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        // Self-registered TENANT_USER: no rows in auth_user_roles yet.
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());

        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        Instant now = Instant.now();
        TokenPair tokenPair = new TokenPair(
                "access-token", "refresh-token",
                now.plusSeconds(900), now.plusSeconds(604800));
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(tokenPair);
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");

        LoginResult result = assertDoesNotThrow(() ->
                service.executeLogin(user, request, "127.0.0.1", "junit-agent", System.currentTimeMillis()));

        assertNotNull(result);

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateTokenPair(claimsCaptor.capture());
        assertTrue(claimsCaptor.getValue().roleIds().isEmpty());

        // The relaxed guard must not treat "no roles yet" as a login failure:
        // recordFailure is only ever invoked from handleFailure(), which the old
        // code path called before throwing UnauthorizedException.
        verify(lockoutService, never()).recordFailure(anyString(), anyString());
        verify(lockoutService).clearFailure(user.getEmail(), "127.0.0.1");
    }

    @Test
    void loginSuccess_publishesLoginSuccessEventAsync() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("existing@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("existing@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");

        service.executeLogin(user, request, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(asyncExecutor, org.mockito.Mockito.atLeastOnce()).execute(runnableCaptor.capture());
        runnableCaptor.getAllValues().forEach(Runnable::run);

        verify(authEventPublisher).publishLoginSuccess(eq(userId), eq(tenantId), any(UUID.class),
                eq("1.2.3.4"), eq("curl/8.0"));
    }

    @Test
    void loginSuccess_singleTenantDeploymentTenantUser_classifiesAsTenantTierNotPlatformTier() {
        // Single-tenant deployments use TenantConstants.PLATFORM_TENANT_ID as the
        // sentinel tenantId for every ordinary user (per its own Javadoc). The
        // audit-tier discriminator must key off UserType, not tenantId, or every
        // login in a single-tenant deployment would be misclassified as platform-tier.
        UUID userId = UUID.randomUUID();
        UUID tenantId = TenantConstants.PLATFORM_TENANT_ID;

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("single-tenant-user@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("single-tenant-user@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");

        service.executeLogin(user, request, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(asyncExecutor, org.mockito.Mockito.atLeastOnce()).execute(runnableCaptor.capture());
        runnableCaptor.getAllValues().forEach(Runnable::run);

        verify(authEventPublisher).publishAuditTenantLoginSuccess(eq(userId), eq(tenantId), any(Instant.class));
        verify(authEventPublisher, never()).publishAuditPlatformLoginSuccess(any(UUID.class), any(Instant.class));
    }

    @Test
    void loginSuccess_superAdmin_classifiesAsPlatformTier() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID(); // not the platform sentinel

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("super-admin@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.SUPER_ADMIN)
                .active(true)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("super-admin@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");

        service.executeLogin(user, request, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(asyncExecutor, org.mockito.Mockito.atLeastOnce()).execute(runnableCaptor.capture());
        runnableCaptor.getAllValues().forEach(Runnable::run);

        verify(authEventPublisher).publishAuditPlatformLoginSuccess(eq(userId), any(Instant.class));
        verify(authEventPublisher, never()).publishAuditTenantLoginSuccess(any(UUID.class), any(UUID.class), any(Instant.class));
    }

    @Test
    void loginFailure_publishesLoginFailedEventAsync() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        service.handleFailure(tenantId, userId, "bad@example.com", "1.2.3.4", "curl/8.0", "INVALID_CREDENTIALS");

        ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(asyncExecutor).execute(runnableCaptor.capture());
        runnableCaptor.getValue().run();

        verify(authEventPublisher).publishLoginFailed("bad@example.com", "1.2.3.4", "INVALID_CREDENTIALS");
    }

    @Test
    void executeLogin_mfaGateReturnsChallenge_returnsChallengeResultWithoutIssuingTokens() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(tenantId)
                .email("mfa-user@example.com").passwordHash("stored-hash")
                .userType(UserType.TENANT_USER).active(true).build();

        LoginRequest request = new LoginRequest();
        request.setEmail("mfa-user@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);

        com.example.authsvc.api.dto.response.MfaChallengeInfo challengeInfo =
                new com.example.authsvc.api.dto.response.MfaChallengeInfo("challenge-token-abc", false);
        when(mfaLoginGate.checkAndIssueChallenge(eq(user), eq("1.2.3.4"), eq("curl/8.0")))
                .thenReturn(java.util.Optional.of(com.example.authsvc.api.dto.response.LoginResult.challenge(challengeInfo)));

        LoginResult result = service.executeLogin(user, request, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        assertThat(result.mfaChallenge()).isNotNull();
        assertThat(result.mfaChallenge().challengeToken()).isEqualTo("challenge-token-abc");
        assertThat(result.accessToken()).isNull();
        verify(jwtUtils, never()).generateTokenPair(any(JwtClaims.class));
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void executeLogin_mfaGateReturnsEmpty_issuesRealTokensAsNormal() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(tenantId)
                .email("no-mfa-user@example.com").passwordHash("stored-hash")
                .userType(UserType.TENANT_USER).active(true).build();

        LoginRequest request = new LoginRequest();
        request.setEmail("no-mfa-user@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(mfaLoginGate.checkAndIssueChallenge(any(), anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");

        LoginResult result = service.executeLogin(user, request, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        assertThat(result.mfaChallenge()).isNull();
        assertThat(result.accessToken()).isEqualTo("access-token");
    }

    @Test
    void proceedPostAuthentication_mfaGateReturnsChallenge_returnsChallengeResultWithoutIssuingTokens() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(tenantId)
                .email("oauth-user@example.com")
                .userType(UserType.TENANT_USER).active(true).build();

        com.example.authsvc.api.dto.response.MfaChallengeInfo challengeInfo =
                new com.example.authsvc.api.dto.response.MfaChallengeInfo("challenge-token-xyz", false);
        when(mfaLoginGate.checkAndIssueChallenge(eq(user), eq("1.2.3.4"), eq("curl/8.0")))
                .thenReturn(java.util.Optional.of(com.example.authsvc.api.dto.response.LoginResult.challenge(challengeInfo)));

        LoginResult result = service.proceedPostAuthentication(user, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        assertThat(result.mfaChallenge()).isNotNull();
        assertThat(result.mfaChallenge().challengeToken()).isEqualTo("challenge-token-xyz");
        assertThat(result.accessToken()).isNull();
        verify(jwtUtils, never()).generateTokenPair(any(JwtClaims.class));
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void proceedPostAuthentication_mfaGateReturnsEmpty_issuesRealTokensWithoutPasswordCheck() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(tenantId)
                .email("oauth-user2@example.com")
                .userType(UserType.TENANT_USER).active(true).build();

        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(mfaLoginGate.checkAndIssueChallenge(any(), anyString(), anyString())).thenReturn(java.util.Optional.empty());
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("");

        LoginResult result = service.proceedPostAuthentication(user, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        assertThat(result.mfaChallenge()).isNull();
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(passwordHasher, never()).verify(anyString(), anyString());
    }

    @Test
    void issueTokens_populatesUsernameUserEmailTenantNameClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId)
                .tenantId(tenantId)
                .email("jane@example.com")
                .passwordHash("stored-hash")
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();

        LoginRequest request = new LoginRequest();
        request.setEmail("jane@example.com");
        request.setPassword("Password123!");

        when(passwordHasher.verify(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(authUserRoleRepository.findRoleIdsByUserId(userId)).thenReturn(List.of());
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        Instant now = Instant.now();
        when(jwtUtils.generateTokenPair(any(JwtClaims.class))).thenReturn(
                new TokenPair("access-token", "refresh-token", now.plusSeconds(900), now.plusSeconds(604800)));
        when(tenantSlugResolver.resolve(tenantId)).thenReturn("acme");
        when(tenantSlugResolver.resolveName(tenantId)).thenReturn("Acme Corp");
        when(userDisplayNameResolver.resolve(userId)).thenReturn("Jane Doe");

        service.executeLogin(user, request, "1.2.3.4", "curl/8.0", System.currentTimeMillis());

        ArgumentCaptor<JwtClaims> claimsCaptor = ArgumentCaptor.forClass(JwtClaims.class);
        verify(jwtUtils).generateTokenPair(claimsCaptor.capture());
        assertThat(claimsCaptor.getValue().username()).isEqualTo("Jane Doe");
        assertThat(claimsCaptor.getValue().userEmail()).isEqualTo("jane@example.com");
        assertThat(claimsCaptor.getValue().tenantName()).isEqualTo("Acme Corp");
    }
}
