package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.ChangePasswordRequest;
import com.example.authsvc.api.dto.request.MfaEnrollConfirmRequest;
import com.example.authsvc.api.dto.request.MfaVerifyLoginRequest;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaEnrollConfirmResponse;
import com.example.authsvc.api.dto.response.MfaEnrollResponse;
import com.example.authsvc.api.dto.response.MfaVerifyLoginResponse;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.application.service.MfaService;
import com.example.authsvc.common.exception.UnauthorizedException;
import com.example.authsvc.domain.model.MfaChallengeEntry;
import com.example.authsvc.domain.model.MfaEnrollment;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.port.MfaChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.authsvc.infrastructure.security.principal.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaControllerTest {

    @Mock private MfaService mfaService;
    @Mock private MfaChallengeStore challengeStore;
    @Mock private AuthUserJpaRepository userRepo;
    @Mock private LoginExecutionService loginExecutor;
    @Mock private AuthCookieFactory cookieFactory;
    @Mock private AuthBehaviorProperties behaviorProps;
    @Mock private AuthenticatedUser principal;
    @Mock private HttpServletRequest httpRequest;

    private MfaController controller;

    @Test
    void enroll_withPrincipal_usesSessionAuth() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();
        when(principal.getUserId()).thenReturn(userId);
        // AuthenticatedUser (built from JWT claims) carries no email field —
        // look it up from the user record, same as the verify-login flow does.
        when(userRepo.findById(userId)).thenReturn(
                Optional.of(AuthUserEntity.builder().id(userId).email("user@example.com").build()));
        when(mfaService.enroll(userId, "user@example.com"))
                .thenReturn(new MfaEnrollment("otpauth://totp/GenAuth:user%40example.com?secret=ABC123", "ABC123"));

        ResponseEntity<MfaEnrollResponse> response = controller.enroll(principal, null);

        assertThat(response.getBody().secret()).isEqualTo("ABC123");
    }

    @Test
    void enroll_withEnrollmentRequiredChallengeToken_succeeds() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();
        // No principal — the caller has never obtained a JWT, since their
        // /login only ever returned a challenge. Resolve via the challenge
        // token instead.
        when(challengeStore.find("token")).thenReturn(Optional.of(new MfaChallengeEntry(userId, true, 0)));
        when(userRepo.findById(userId)).thenReturn(
                Optional.of(AuthUserEntity.builder().id(userId).email("user@example.com").build()));
        when(mfaService.enroll(userId, "user@example.com"))
                .thenReturn(new MfaEnrollment("otpauth://totp/GenAuth:user%40example.com?secret=ABC123", "ABC123"));

        ResponseEntity<MfaEnrollResponse> response = controller.enroll(null, "token");

        assertThat(response.getBody().secret()).isEqualTo("ABC123");
    }

    @Test
    void enroll_withNonEnrollmentRequiredChallengeToken_throwsUnauthorized() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();
        // A normal (already-enrolled) login challenge must never be usable to
        // generate a NEW secret without proving control of the existing device.
        when(challengeStore.find("token")).thenReturn(Optional.of(new MfaChallengeEntry(userId, false, 0)));

        org.junit.jupiter.api.Assertions.assertThrows(UnauthorizedException.class,
                () -> controller.enroll(null, "token"));

        verify(mfaService, never()).enroll(any(), any());
    }

    @Test
    void enroll_withUnknownChallengeToken_throwsUnauthorized() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        when(challengeStore.find("bad-token")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(UnauthorizedException.class,
                () -> controller.enroll(null, "bad-token"));
    }

    @Test
    void enroll_withNeitherPrincipalNorChallengeToken_throwsUnauthorized() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);

        org.junit.jupiter.api.Assertions.assertThrows(UnauthorizedException.class,
                () -> controller.enroll(null, null));
    }

    @Test
    void confirmEnrollment_validCode_returnsBackupCodes() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();
        when(principal.getUserId()).thenReturn(userId);
        when(mfaService.confirmEnrollment(userId, "123456"))
                .thenReturn(List.of("CODE1", "CODE2"));

        ResponseEntity<MfaEnrollConfirmResponse> response =
                controller.confirmEnrollment(principal, new MfaEnrollConfirmRequest("123456"));

        assertThat(response.getBody().backupCodes()).containsExactly("CODE1", "CODE2");
    }

    @Test
    void verifyLogin_unknownChallengeToken_throwsUnauthorized() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        when(challengeStore.find("bad-token")).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(UnauthorizedException.class,
                () -> controller.verifyLogin(new MfaVerifyLoginRequest("bad-token", "123456"), httpRequest));
    }

    @Test
    void verifyLogin_attemptLimitExceeded_deletesChallengeAndThrowsUnauthorized() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();
        when(challengeStore.find("token")).thenReturn(Optional.of(new MfaChallengeEntry(userId, false, 5)));

        org.junit.jupiter.api.Assertions.assertThrows(UnauthorizedException.class,
                () -> controller.verifyLogin(new MfaVerifyLoginRequest("token", "000000"), httpRequest));

        verify(challengeStore).delete("token");
    }

    @Test
    void verifyLogin_validCode_deletesChallengeAndIssuesTokens() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder().id(userId).email("user@example.com").build();

        when(challengeStore.find("token")).thenReturn(Optional.of(new MfaChallengeEntry(userId, false, 0)));
        when(mfaService.verifyCode(userId, "123456")).thenReturn(true);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        // response() must be populated — same as real LoginExecutionService.issueTokens
        // always returns — since the controller's JSON branch (mirroring
        // AuthController.login()) reads userId/email/accessTokenExpiresAt off it.
        com.example.authsvc.api.dto.response.LoginResponse loginResponse =
                new com.example.authsvc.api.dto.response.LoginResponse(
                        userId, "user@example.com", java.time.Instant.now().plusSeconds(900));
        when(loginExecutor.issueTokens(any(), any(), any(), any(), any(Long.class)))
                .thenReturn(new LoginResult(loginResponse, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7)));
        // JSON delivery mode avoids needing to mock AuthCookieFactory's cookie
        // builders — the cookie-mode branch is already covered end-to-end by
        // AuthControllerTest for the equivalent /login path.
        when(behaviorProps.isJsonTokenDelivery()).thenReturn(true);

        ResponseEntity<?> response = controller.verifyLogin(new MfaVerifyLoginRequest("token", "123456"), httpRequest);

        verify(challengeStore).delete("token");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void verifyLogin_enrollmentBranchSucceeds_returnsBackupCodesInResponse() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder().id(userId).email("user@example.com").build();
        List<String> backupCodes = List.of("AAAA1111", "BBBB2222", "CCCC3333");

        when(challengeStore.find("token")).thenReturn(Optional.of(new MfaChallengeEntry(userId, true, 0)));
        when(mfaService.isEnrolled(userId)).thenReturn(false);
        when(mfaService.confirmEnrollment(userId, "123456")).thenReturn(backupCodes);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        com.example.authsvc.api.dto.response.LoginResponse loginResponse =
                new com.example.authsvc.api.dto.response.LoginResponse(
                        userId, "user@example.com", java.time.Instant.now().plusSeconds(900));
        when(loginExecutor.issueTokens(any(), any(), any(), any(), any(Long.class)))
                .thenReturn(new LoginResult(loginResponse, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7)));
        when(behaviorProps.isJsonTokenDelivery()).thenReturn(true);

        ResponseEntity<?> response = controller.verifyLogin(new MfaVerifyLoginRequest("token", "123456"), httpRequest);

        verify(challengeStore).delete("token");
        assertThat(response.getBody()).isInstanceOf(MfaVerifyLoginResponse.class);
        MfaVerifyLoginResponse body = (MfaVerifyLoginResponse) response.getBody();
        assertThat(body.backupCodes()).containsExactlyElementsOf(backupCodes);
        assertThat(body.userId()).isEqualTo(userId);
        assertThat(body.email()).isEqualTo("user@example.com");
        assertThat(body.accessToken()).isEqualTo("access-tok");
        assertThat(body.refreshToken()).isEqualTo("refresh-tok");
    }

    @Test
    void verifyLogin_enrollmentBranchWrongCode_incrementsAttemptsAndThrows() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();

        when(challengeStore.find("token")).thenReturn(Optional.of(new MfaChallengeEntry(userId, true, 0)));
        when(mfaService.isEnrolled(userId)).thenReturn(false);
        when(mfaService.confirmEnrollment(userId, "000000")).thenThrow(new UnauthorizedException());

        org.junit.jupiter.api.Assertions.assertThrows(UnauthorizedException.class,
                () -> controller.verifyLogin(new MfaVerifyLoginRequest("token", "000000"), httpRequest));

        verify(challengeStore).incrementAttempts("token");
        verify(challengeStore, never()).delete("token");
    }

    @Test
    void verifyLogin_normalVerifyBranch_stillReturnsPlainLoginResponse() {
        controller = new MfaController(mfaService, challengeStore, userRepo, loginExecutor, cookieFactory, behaviorProps);
        UUID userId = UUID.randomUUID();
        AuthUserEntity user = AuthUserEntity.builder().id(userId).email("user@example.com").build();

        when(challengeStore.find("token")).thenReturn(Optional.of(new MfaChallengeEntry(userId, false, 0)));
        when(mfaService.verifyCode(userId, "123456")).thenReturn(true);
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        com.example.authsvc.api.dto.response.LoginResponse loginResponse =
                new com.example.authsvc.api.dto.response.LoginResponse(
                        userId, "user@example.com", java.time.Instant.now().plusSeconds(900));
        when(loginExecutor.issueTokens(any(), any(), any(), any(), any(Long.class)))
                .thenReturn(new LoginResult(loginResponse, "access-tok", "refresh-tok", Duration.ofMinutes(15), Duration.ofDays(7)));
        when(behaviorProps.isJsonTokenDelivery()).thenReturn(true);

        ResponseEntity<?> response = controller.verifyLogin(new MfaVerifyLoginRequest("token", "123456"), httpRequest);

        assertThat(response.getBody()).isInstanceOf(com.example.authsvc.api.dto.response.LoginResponse.class);
        assertThat(response.getBody()).isNotInstanceOf(MfaVerifyLoginResponse.class);
        verify(mfaService, never()).confirmEnrollment(any(), any());
    }
}
