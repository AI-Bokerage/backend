package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.request.LoginRequest;
import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaChallengeInfo;
import com.example.authsvc.application.impl.InternalSessionRevocationService;
import com.example.authsvc.application.service.ChangePasswordService;
import com.example.authsvc.application.service.LoginService;
import com.example.authsvc.application.service.RefreshTokenService;
import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.application.service.SessionService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers only the new MFA-challenge branch of {@code AuthController.login(...)} —
 * the existing real-token happy path is already covered by whatever tests this
 * controller had before MFA (not duplicated here).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerLoginMfaTest {

    @Mock private LoginService loginService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SessionService sessionService;
    @Mock private ChangePasswordService changePasswordService;
    @Mock private AuthCookieFactory cookieFactory;
    @Mock private AuthBehaviorProperties behaviorProps;
    @Mock private RegisterService registerService;
    @Mock private InternalSessionRevocationService revocationService;
    @Mock private HttpServletRequest httpRequest;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(loginService, refreshTokenService, sessionService,
                changePasswordService, cookieFactory, behaviorProps, registerService, revocationService);
    }

    @Test
    void login_mfaChallengeReturned_respondsWithChallengeBodyNotTokens() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password123!");

        when(httpRequest.getRemoteAddr()).thenReturn("1.2.3.4");
        when(httpRequest.getHeader("User-Agent")).thenReturn("curl/8.0");

        LoginResult challengeResult = LoginResult.challenge(new MfaChallengeInfo("challenge-tok", true));
        when(loginService.login(any(LoginRequest.class), any(), any())).thenReturn(challengeResult);

        ResponseEntity<?> response = controller.login(request, httpRequest);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(com.example.authsvc.api.dto.response.MfaLoginChallengeResponse.class);
        var body = (com.example.authsvc.api.dto.response.MfaLoginChallengeResponse) response.getBody();
        assertThat(body.mfaRequired()).isTrue();
        assertThat(body.challengeToken()).isEqualTo("challenge-tok");
        assertThat(body.enrollmentRequired()).isTrue();
    }
}
