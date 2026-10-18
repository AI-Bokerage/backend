package com.example.authsvc.infrastructure.security.oauth;

import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserOAuthIdentityEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserOAuthIdentityJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.application.service.LoginExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthLoginSuccessHandlerTest {

    @Mock private AuthUserJpaRepository userRepo;
    @Mock private AuthUserOAuthIdentityJpaRepository identityRepo;
    @Mock private OAuthSignupChallengeStore signupChallengeStore;
    @Mock private LoginExecutionService loginExecutor;
    @Mock private AuthCookieFactory cookieFactory;
    @Mock private PlatformTransactionManager txManager;

    private AuthBehaviorProperties behaviorProps;
    private OAuthLoginSuccessHandler handler;

    @BeforeEach
    void setUp() {
        behaviorProps = new AuthBehaviorProperties();
        behaviorProps.setTokenDeliveryMode("json");
        handler = new OAuthLoginSuccessHandler(
                userRepo, identityRepo, signupChallengeStore, loginExecutor, cookieFactory, behaviorProps, txManager);
    }

    private OidcUser oidcUser(String subject, String email) {
        return oidcUser(subject, email, true);
    }

    private OidcUser oidcUser(String subject, String email, boolean emailVerified) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", subject);
        claims.put("email", email);
        claims.put("email_verified", emailVerified);
        OidcIdToken idToken = new OidcIdToken("id-token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        return new DefaultOidcUser(List.of(), idToken);
    }

    private OAuth2AuthenticationToken authToken(OidcUser user) {
        return new OAuth2AuthenticationToken(user, Collections.emptyList(), "google");
    }

    @Test
    void existingIdentity_logsInWithoutCreatingAnything() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUserEntity existing = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("linked@example.com").passwordHash("hash")
                .userType(UserType.TENANT_USER).active(true).build();
        AuthUserOAuthIdentityEntity identity = AuthUserOAuthIdentityEntity.builder()
                .id(UUID.randomUUID()).userId(userId).provider("google")
                .providerSubject("sub-123").email("linked@example.com").build();

        when(identityRepo.findByProviderAndProviderSubject("google", "sub-123")).thenReturn(Optional.of(identity));
        when(userRepo.findById(userId)).thenReturn(Optional.of(existing));

        Instant now = Instant.now();
        LoginResponse response = new LoginResponse(userId, existing.getEmail(), now.plusSeconds(900), "access-tok", "refresh-tok");
        LoginResult result = new LoginResult(response, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7));
        when(loginExecutor.proceedPostAuthentication(any(), anyString(), nullable(String.class), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(result);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, httpResponse, authToken(oidcUser("sub-123", "linked@example.com")));

        verify(userRepo, org.mockito.Mockito.never()).save(any());
        verify(identityRepo, org.mockito.Mockito.never()).save(any());
        assertThat(httpResponse.getContentAsString()).contains("access-tok");
    }

    @Test
    void existingIdentityWithNoPassword_reIssuesSetupChallengeInsteadOfTokens() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUserEntity pending = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("never-finished@example.com").passwordHash(null)
                .userType(UserType.TENANT_USER).active(true).build();
        AuthUserOAuthIdentityEntity identity = AuthUserOAuthIdentityEntity.builder()
                .id(UUID.randomUUID()).userId(userId).provider("google")
                .providerSubject("sub-pending").email("never-finished@example.com").build();

        when(identityRepo.findByProviderAndProviderSubject("google", "sub-pending")).thenReturn(Optional.of(identity));
        when(userRepo.findById(userId)).thenReturn(Optional.of(pending));
        when(signupChallengeStore.issue(any(), any())).thenReturn("setup-token-again");

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), httpResponse,
                authToken(oidcUser("sub-pending", "never-finished@example.com")));

        verify(signupChallengeStore).issue(userId, Duration.ofMinutes(10));
        verify(loginExecutor, never())
                .proceedPostAuthentication(any(), nullable(String.class), nullable(String.class), org.mockito.ArgumentMatchers.anyLong());
        assertThat(httpResponse.getContentAsString()).contains("setup-token-again").contains("passwordSetupRequired");
    }

    @Test
    void existingIdentityButInactiveUser_rejectsWithForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUserEntity deactivated = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("deactivated@example.com").passwordHash("hash")
                .userType(UserType.TENANT_USER).active(false).build();
        AuthUserOAuthIdentityEntity identity = AuthUserOAuthIdentityEntity.builder()
                .id(UUID.randomUUID()).userId(userId).provider("google")
                .providerSubject("sub-inactive").email("deactivated@example.com").build();

        when(identityRepo.findByProviderAndProviderSubject("google", "sub-inactive")).thenReturn(Optional.of(identity));
        when(userRepo.findById(userId)).thenReturn(Optional.of(deactivated));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), httpResponse,
                authToken(oidcUser("sub-inactive", "deactivated@example.com")));

        assertThat(httpResponse.getStatus()).isEqualTo(403);
        verify(loginExecutor, never())
                .proceedPostAuthentication(any(), nullable(String.class), nullable(String.class), org.mockito.ArgumentMatchers.anyLong());
        verify(signupChallengeStore, never()).issue(any(), any());
        verify(userRepo, never()).save(any());
        verify(identityRepo, never()).save(any());
    }

    @Test
    void providerEmailWithMixedCase_isNormalizedBeforeLookup() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUserEntity existing = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("mixed@example.com").passwordHash("hash")
                .userType(UserType.TENANT_USER).active(true).build();

        when(identityRepo.findByProviderAndProviderSubject("google", "sub-case")).thenReturn(Optional.empty());
        when(userRepo.findByEmailAndActiveTrue("mixed@example.com")).thenReturn(Optional.of(existing));

        Instant now = Instant.now();
        LoginResponse response = new LoginResponse(userId, existing.getEmail(), now.plusSeconds(900), "access-tok", "refresh-tok");
        when(loginExecutor.proceedPostAuthentication(any(), anyString(), nullable(String.class), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new LoginResult(response, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7)));

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                authToken(oidcUser("sub-case", "  Mixed@Example.COM  ")));

        ArgumentCaptor<AuthUserOAuthIdentityEntity> captor = ArgumentCaptor.forClass(AuthUserOAuthIdentityEntity.class);
        verify(identityRepo).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("mixed@example.com");
    }

    @Test
    void providerWithoutEmailClaim_rejectsGenerically() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", "sub-no-email");
        OidcIdToken idToken = new OidcIdToken("id-token-value", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser noEmailUser = new DefaultOidcUser(List.of(), idToken);

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), httpResponse, authToken(noEmailUser));

        assertThat(httpResponse.getStatus()).isEqualTo(401);
        assertThat(httpResponse.getContentAsString()).contains("oauth_login_failed");
        verify(userRepo, never()).save(any());
        verify(identityRepo, never()).save(any());
        verify(loginExecutor, never())
                .proceedPostAuthentication(any(), nullable(String.class), nullable(String.class), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void noIdentityButEmailMatches_linksToExistingAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUserEntity existing = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("password-user@example.com").passwordHash("hash")
                .userType(UserType.TENANT_USER).active(true).build();

        when(identityRepo.findByProviderAndProviderSubject("google", "sub-456")).thenReturn(Optional.empty());
        when(userRepo.findByEmailAndActiveTrue("password-user@example.com")).thenReturn(Optional.of(existing));

        Instant now = Instant.now();
        LoginResponse response = new LoginResponse(userId, existing.getEmail(), now.plusSeconds(900), "access-tok", "refresh-tok");
        when(loginExecutor.proceedPostAuthentication(any(), anyString(), nullable(String.class), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new LoginResult(response, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7)));

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                authToken(oidcUser("sub-456", "password-user@example.com")));

        ArgumentCaptor<AuthUserOAuthIdentityEntity> captor = ArgumentCaptor.forClass(AuthUserOAuthIdentityEntity.class);
        verify(identityRepo).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getProviderSubject()).isEqualTo("sub-456");
        verify(userRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void noIdentityButEmailMatchesUnverified_rejectsWithConflict() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUserEntity existing = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("collision@example.com").passwordHash("hash")
                .userType(UserType.TENANT_USER).active(true).build();

        when(identityRepo.findByProviderAndProviderSubject("google", "sub-unverified")).thenReturn(Optional.empty());
        when(userRepo.findByEmailAndActiveTrue("collision@example.com")).thenReturn(Optional.of(existing));

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), httpResponse,
                authToken(oidcUser("sub-unverified", "collision@example.com", false)));

        verify(identityRepo, org.mockito.Mockito.never()).save(any());
        verify(userRepo, org.mockito.Mockito.never()).save(any());
        verify(loginExecutor, org.mockito.Mockito.never())
                .proceedPostAuthentication(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(httpResponse.getStatus()).isEqualTo(409);
    }

    @Test
    void noMatchAtAll_createsBlockedAccountAndIssuesSignupChallenge() throws Exception {
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(identityRepo.findByProviderAndProviderSubject("google", "sub-789")).thenReturn(Optional.empty());
        when(userRepo.findByEmailAndActiveTrue("brand-new@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(signupChallengeStore.issue(any(), any())).thenReturn("setup-token-abc");

        MockHttpServletResponse httpResponse = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(new MockHttpServletRequest(), httpResponse,
                authToken(oidcUser("sub-789", "brand-new@example.com")));

        ArgumentCaptor<AuthUserEntity> userCaptor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isNull();
        assertThat(userCaptor.getValue().getTenantId()).isEqualTo(TenantConstants.PLATFORM_TENANT_ID);
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("brand-new@example.com");

        verify(identityRepo).save(any());
        verify(loginExecutor, org.mockito.Mockito.never())
                .proceedPostAuthentication(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(httpResponse.getContentAsString()).contains("setup-token-abc").contains("passwordSetupRequired");
    }

    @Test
    void noMatchAtAll_withTenantIdOnRequestAttribute_usesCapturedTenant() throws Exception {
        UUID tenantId = UUID.randomUUID();
        when(txManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(identityRepo.findByProviderAndProviderSubject("google", "sub-999")).thenReturn(Optional.empty());
        when(userRepo.findByEmailAndActiveTrue("tenant-scoped@example.com")).thenReturn(Optional.empty());
        when(userRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(signupChallengeStore.issue(any(), any())).thenReturn("setup-token-def");

        MockHttpServletRequest request = new MockHttpServletRequest();
        var capturedAuthRequest = org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("client-id")
                .redirectUri("https://app.example.com/login/oauth2/code/google")
                .scopes(java.util.Set.of("openid"))
                .state("state-1")
                .attributes(Map.of("tenantId", tenantId.toString()))
                .build();
        request.setAttribute(RedisOAuth2AuthorizationRequestRepository.REQUEST_ATTRIBUTE_NAME, capturedAuthRequest);

        handler.onAuthenticationSuccess(request, new MockHttpServletResponse(),
                authToken(oidcUser("sub-999", "tenant-scoped@example.com")));

        ArgumentCaptor<AuthUserEntity> userCaptor = ArgumentCaptor.forClass(AuthUserEntity.class);
        verify(userRepo).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    }
}
