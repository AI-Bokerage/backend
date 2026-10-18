package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.OAuthCompleteSignupRequest;
import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaChallengeInfo;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.password.PasswordHasher;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    @Mock private OAuthSignupChallengeStore signupChallengeStore;
    @Mock private AuthUserJpaRepository userRepo;
    @Mock private PasswordHasher passwordHasher;
    @Mock private LoginExecutionService loginExecutor;
    @Mock private AuthCookieFactory cookieFactory;
    @Mock private HttpServletRequest httpRequest;

    private AuthBehaviorProperties behaviorProps;
    private OAuthController controller;

    @BeforeEach
    void setUp() {
        behaviorProps = new AuthBehaviorProperties();
        controller = new OAuthController(signupChallengeStore, userRepo, passwordHasher, loginExecutor, cookieFactory, behaviorProps);
    }

    @Test
    void completeSignup_validToken_setsPasswordAndIssuesTokens() {
        UUID userId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("new-oauth-user@example.com")
                .userType(UserType.TENANT_USER).active(true).build();

        when(signupChallengeStore.find("setup-token")).thenReturn(Optional.of(new OAuthSignupChallengeEntry(userId)));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.hash("NewPassword123!")).thenReturn("hashed-password");

        Instant now = Instant.now();
        LoginResponse response = new LoginResponse(userId, user.getEmail(), now.plusSeconds(900), "access-tok", "refresh-tok");
        LoginResult result = new LoginResult(response, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7));
        when(loginExecutor.proceedPostAuthentication(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(result);
        when(cookieFactory.createAccessTokenCookie(anyString(), any())).thenReturn(
                ResponseCookie.from("access_token", "access-tok").build());
        when(cookieFactory.createRefreshTokenCookie(anyString(), any())).thenReturn(
                ResponseCookie.from("refresh_token", "refresh-tok").build());
        when(cookieFactory.clearLegacyRefreshTokenCookie()).thenReturn(ResponseCookie.from("legacy_rt", "").build());
        when(cookieFactory.clearOldNarrowRefreshTokenCookie()).thenReturn(ResponseCookie.from("old_rt", "").build());

        behaviorProps.setTokenDeliveryMode("cookie");
        ResponseEntity<?> httpResponse = controller.completeSignup(
                new OAuthCompleteSignupRequest("setup-token", "NewPassword123!"), httpRequest);

        assertThat(httpResponse.getStatusCode().is2xxSuccessful()).isTrue();
        verify(passwordHasher).hash("NewPassword123!");
        verify(userRepo).save(user);
        assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        verify(signupChallengeStore).delete("setup-token");
    }

    @Test
    void completeSignup_unknownToken_throwsUnauthorized() {
        when(signupChallengeStore.find("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.completeSignup(
                new OAuthCompleteSignupRequest("bad-token", "NewPassword123!"), httpRequest))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void completeSignup_deactivatedUser_throwsUnauthorized() {
        UUID userId = UUID.randomUUID();
        AuthUserEntity deactivated = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("deactivated@example.com")
                .userType(UserType.TENANT_USER).active(false).build();

        when(signupChallengeStore.find("setup-token")).thenReturn(Optional.of(new OAuthSignupChallengeEntry(userId)));
        when(userRepo.findById(userId)).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> controller.completeSignup(
                new OAuthCompleteSignupRequest("setup-token", "NewPassword123!"), httpRequest))
                .isInstanceOf(UnauthorizedException.class);

        verify(userRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void completeSignup_tenantRequiresMfa_returnsChallengeInsteadOfTokens() {
        UUID userId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder()
                .id(userId).tenantId(UUID.randomUUID())
                .email("mfa-tenant-user@example.com")
                .userType(UserType.TENANT_USER).active(true).build();

        when(signupChallengeStore.find("setup-token")).thenReturn(Optional.of(new OAuthSignupChallengeEntry(userId)));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.hash(anyString())).thenReturn("hashed-password");
        when(loginExecutor.proceedPostAuthentication(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(LoginResult.challenge(new MfaChallengeInfo("mfa-challenge-token", false)));

        ResponseEntity<?> httpResponse = controller.completeSignup(
                new OAuthCompleteSignupRequest("setup-token", "NewPassword123!"), httpRequest);

        assertThat(httpResponse.getBody()).isInstanceOf(com.example.authsvc.api.dto.response.MfaLoginChallengeResponse.class);
    }
}
