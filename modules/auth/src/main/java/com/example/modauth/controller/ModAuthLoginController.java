package com.example.modauth.controller;

import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.example.modauth.dto.ModLoginRequest;
import com.example.modauth.dto.ModLoginResponse;
import com.example.modauth.service.ModAuthLoginResult;
import com.example.modauth.service.ModAuthLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/modauth/login} — a distinct path from the starter's own
 * {@code /api/v1/auth/login} (that one stays available as-is) because this
 * one runs the extra account-status/terms/role decisions from the "Logging
 * in" diagram before handing off to the same token-issuing code path.
 */
@Tag(name = "Login", description = "Role-aware login — lockout, account-active, terms and dashboard routing")
@RestController
@RequestMapping("/api/v1/modauth/login")
@RequiredArgsConstructor
public class ModAuthLoginController {

    private final ModAuthLoginService loginService;
    private final AuthCookieFactory cookieFactory;
    private final AuthBehaviorProperties behaviorProps;

    @Operation(summary = "Log in", description = "Runs the full \"Logging in\" flow: lockout guard, credential check, "
            + "account-active check, terms-changed gate, then issues tokens and a dashboard hint. "
            + "On the fifth failed attempt in the window the account locks for 15 minutes (429) and a warning "
            + "email is sent. A suspended or cancelled brokerage gets 403.")
    @ApiResponse(responseCode = "200", description = "Either real tokens, or requiresTermsAcceptance=true with none yet")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @ApiResponse(responseCode = "403", description = "Account deactivated — contact your admin")
    @ApiResponse(responseCode = "429", description = "Account locked after 5 failed attempts")
    @PostMapping
    public ResponseEntity<ModLoginResponse> login(@Valid @RequestBody ModLoginRequest request,
                                                   HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        String userAgent = httpRequest.getHeader("User-Agent");

        ModAuthLoginResult result = loginService.login(request, ip, userAgent);

        if (result.requiresTermsAcceptance()) {
            return ResponseEntity.ok(ModLoginResponse.termsGate(result.termsVersion()));
        }

        LoginResult starterResult = result.starterResult();
        var body = starterResult.response();
        ModLoginResponse response = new ModLoginResponse(
                body.getUserId(),
                body.getEmail(),
                result.role(),
                result.dashboard(),
                false,
                null,
                behaviorProps.isJsonTokenDelivery() ? starterResult.accessToken() : null,
                behaviorProps.isJsonTokenDelivery() ? starterResult.refreshToken() : null,
                body.getAccessTokenExpiresAt()
        );

        if (behaviorProps.isJsonTokenDelivery()) {
            return ResponseEntity.ok(response);
        }

        var accessCookie = cookieFactory.createAccessTokenCookie(starterResult.accessToken(), starterResult.accessTokenTtl());
        var refreshCookie = cookieFactory.createRefreshTokenCookie(starterResult.refreshToken(), starterResult.refreshTokenTtl());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }
}
