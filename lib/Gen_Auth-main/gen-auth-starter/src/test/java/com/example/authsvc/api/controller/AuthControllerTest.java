package com.example.authsvc.api.controller;

import com.example.authsvc.api.dto.response.LoginResponse;
import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.application.impl.InternalSessionRevocationService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.application.service.ChangePasswordService;
import com.example.authsvc.application.service.LoginService;
import com.example.authsvc.application.service.RefreshTokenService;
import com.example.authsvc.application.service.RegisterService;
import com.example.authsvc.application.service.SessionService;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private LoginService loginService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SessionService sessionService;
    @Mock private ChangePasswordService changePasswordService;
    @Mock private AuthCookieFactory cookieFactory;
    @Mock private RegisterService registerService;
    @Mock private InternalSessionRevocationService revocationService;
    @Mock private HttpServletRequest httpRequest;

    private AuthController controller(AuthBehaviorProperties props) {
        return new AuthController(
                loginService, refreshTokenService, sessionService, changePasswordService, cookieFactory, props,
                registerService, revocationService);
    }

    private LoginResult sampleLoginResult() {
        LoginResponse response = new LoginResponse(
                UUID.randomUUID(), "user@example.com", Instant.now().plusSeconds(900));
        return new LoginResult(response, "access-token-value", "refresh-token-value",
                Duration.ofMinutes(15), Duration.ofDays(30));
    }

    @Test
    void jsonModeReturnsTokensInBodyNotCookies() {
        AuthBehaviorProperties props = new AuthBehaviorProperties();
        props.setTokenDeliveryMode("json");
        when(loginService.login(any(), any(), any())).thenReturn(sampleLoginResult());

        ResponseEntity<?> result = controller(props).login(
                new com.example.authsvc.api.dto.request.LoginRequest(), httpRequest);

        LoginResponse body = (LoginResponse) result.getBody();
        assertEquals("access-token-value", body.getAccessToken());
        assertEquals("refresh-token-value", body.getRefreshToken());
        assertNull(result.getHeaders().get(HttpHeaders.SET_COOKIE));
    }

    @Test
    void cookieModeSetsCookiesAndOmitsRefreshTokenFromBody() {
        AuthBehaviorProperties props = new AuthBehaviorProperties(); // default = cookie
        when(loginService.login(any(), any(), any())).thenReturn(sampleLoginResult());
        when(cookieFactory.createAccessTokenCookie(any(), any()))
                .thenReturn(org.springframework.http.ResponseCookie.from("access_token", "x").build());
        when(cookieFactory.createRefreshTokenCookie(any(), any()))
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "y").build());
        when(cookieFactory.clearLegacyRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "").build());
        when(cookieFactory.clearOldNarrowRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "").build());

        ResponseEntity<?> result = controller(props).login(
                new com.example.authsvc.api.dto.request.LoginRequest(), httpRequest);

        assertNull(((LoginResponse) result.getBody()).getRefreshToken());
        assertEquals(4, result.getHeaders().get(HttpHeaders.SET_COOKIE).size());
    }

    @Test
    void logoutAllRevokesEverySessionForTheCallingUser() {
        AuthBehaviorProperties props = new AuthBehaviorProperties();
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        com.example.authsvc.infrastructure.security.principal.AuthenticatedUser principal =
                new com.example.authsvc.infrastructure.security.principal.AuthenticatedUser(
                        userId, tenantId, "platform", java.util.List.of(), com.example.authsvc.domain.enums.UserType.TENANT_USER,
                        UUID.randomUUID().toString(), Instant.now().plusSeconds(900), UUID.randomUUID().toString());
        when(cookieFactory.clearAccessTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("access_token", "").build());
        when(cookieFactory.clearRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "").build());
        when(cookieFactory.clearLegacyRefreshTokenCookie())
                .thenReturn(org.springframework.http.ResponseCookie.from("refresh_token", "").build());

        controller(props).logoutAll(principal);

        verify(revocationService).revokeAllForUser(userId, tenantId);
    }

    private static <T> T any() { return org.mockito.ArgumentMatchers.any(); }
}
