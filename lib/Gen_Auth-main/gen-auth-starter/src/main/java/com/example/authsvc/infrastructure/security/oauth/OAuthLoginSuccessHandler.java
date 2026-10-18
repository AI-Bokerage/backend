package com.example.authsvc.infrastructure.security.oauth;

import com.example.authsvc.api.dto.response.LoginResult;
import com.example.authsvc.api.dto.response.MfaLoginChallengeResponse;
import com.example.authsvc.api.dto.response.OAuthPasswordSetupRequiredResponse;
import com.example.authsvc.application.service.LoginExecutionService;
import com.example.authsvc.config.properties.AuthBehaviorProperties;
import com.example.authsvc.domain.TenantConstants;
import com.example.authsvc.domain.enums.UserType;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserEntity;
import com.example.authsvc.infrastructure.persistence.entity.AuthUserOAuthIdentityEntity;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserJpaRepository;
import com.example.authsvc.infrastructure.persistence.repository.AuthUserOAuthIdentityJpaRepository;
import com.example.authsvc.infrastructure.security.cookie.AuthCookieFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs after Spring Security's {@code oauth2Login()} has already exchanged
 * the authorization code, fetched the ID token, and validated it (issuer,
 * audience, expiry, nonce, signature — all handled by the framework). Applies
 * this service's own account-linking/blocking-gate decision (see the design
 * spec) and issues this service's own JWTs, writing the HTTP response
 * directly instead of delegating to Spring's default redirect behavior.
 */
@Slf4j
public class OAuthLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Duration SIGNUP_CHALLENGE_TTL = Duration.ofMinutes(10);
    private static final String TENANT_ID_PARAM = "tenantId";

    private final AuthUserJpaRepository              userRepo;
    private final AuthUserOAuthIdentityJpaRepository identityRepo;
    private final OAuthSignupChallengeStore          signupChallengeStore;
    private final LoginExecutionService              loginExecutor;
    private final AuthCookieFactory                  cookieFactory;
    private final AuthBehaviorProperties             behaviorProps;
    private final TransactionTemplate                txTemplate;

    public OAuthLoginSuccessHandler(AuthUserJpaRepository userRepo,
                                    AuthUserOAuthIdentityJpaRepository identityRepo,
                                    OAuthSignupChallengeStore signupChallengeStore,
                                    LoginExecutionService loginExecutor,
                                    AuthCookieFactory cookieFactory,
                                    AuthBehaviorProperties behaviorProps,
                                    PlatformTransactionManager txManager) {
        this.userRepo             = userRepo;
        this.identityRepo         = identityRepo;
        this.signupChallengeStore = signupChallengeStore;
        this.loginExecutor        = loginExecutor;
        this.cookieFactory        = cookieFactory;
        this.behaviorProps        = behaviorProps;
        this.txTemplate           = new TransactionTemplate(txManager);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OidcUser oidcUser = (OidcUser) oauthToken.getPrincipal();
        String provider = oauthToken.getAuthorizedClientRegistrationId();
        String subject  = oidcUser.getSubject();
        String rawEmail = oidcUser.getEmail();

        // A provider that returns no email claim can neither be matched nor
        // used to create an account (auth_users.email is NOT NULL/unique), and
        // letting it through would surface as an unhandled 500 out of a
        // success handler. Normalize case the same way every other read path
        // in this service does (LoginServiceImpl, MagicLinkServiceImpl).
        if (rawEmail == null || rawEmail.isBlank()) {
            log.warn("oauth.login.missing_email provider={}", provider);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            writeJson(response, Map.of("error", "oauth_login_failed"));
            return;
        }
        final String email = rawEmail.trim().toLowerCase(Locale.ROOT);

        String ip        = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        AuthUserEntity user;
        Optional<AuthUserOAuthIdentityEntity> identity =
                identityRepo.findByProviderAndProviderSubject(provider, subject);

        if (identity.isPresent()) {
            user = userRepo.findById(identity.get().getUserId())
                    .orElseThrow(() -> new IllegalStateException(
                            "auth_user_oauth_identity references missing user " + identity.get().getUserId()));
            // Deactivated accounts are locked out of every other login path
            // (findByEmailAndActiveTrue / the magic-link isActive filter);
            // reject generically rather than falling through to the
            // email-match branch, whose unique email would still resolve.
            if (!user.isActive()) {
                log.warn("oauth.login.inactive_user_rejected userId={} provider={}", user.getId(), provider);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                writeJson(response, Map.of("error", "account_inactive"));
                return;
            }
        } else {
            Optional<AuthUserEntity> emailMatch = userRepo.findByEmailAndActiveTrue(email);
            if (emailMatch.isEmpty()) {
                AuthUserEntity created = txTemplate.execute(status -> {
                    AuthUserEntity newUser = createPendingUser(resolveTenantId(request), email);
                    saveIdentity(provider, subject, email, newUser.getId());
                    return newUser;
                });
                String setupToken = signupChallengeStore.issue(created.getId(), SIGNUP_CHALLENGE_TTL);
                log.info("oauth.signup.pending userId={} provider={}", created.getId(), provider);
                writeJson(response, new OAuthPasswordSetupRequiredResponse(true, setupToken));
                return;
            }
            // auth_users.email is unique, so an unverified-email collision can only be
            // rejected here — it can neither be auto-linked (unproven ownership) nor
            // fall through to create-new (the email is already taken).
            if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
                log.warn("oauth.login.email_unverified_collision provider={}", provider);
                response.setStatus(HttpServletResponse.SC_CONFLICT);
                writeJson(response, Map.of("error", "oauth_email_not_verified"));
                return;
            }
            saveIdentity(provider, subject, email, emailMatch.get().getId());
            user = emailMatch.get();
        }

        // Shared gate for both existing-account paths: an account created by a
        // previous OAuth login that never completed /complete-signup has no
        // password yet. Without this, repeating the OAuth login would hand it
        // real tokens and bypass the blocking gate entirely.
        if (user.getPasswordHash() == null) {
            String setupToken = signupChallengeStore.issue(user.getId(), SIGNUP_CHALLENGE_TTL);
            log.info("oauth.login.pending_password_setup userId={} provider={}", user.getId(), provider);
            writeJson(response, new OAuthPasswordSetupRequiredResponse(true, setupToken));
            return;
        }

        LoginResult result = loginExecutor.proceedPostAuthentication(user, ip, userAgent, System.currentTimeMillis());
        log.info("oauth.login.completed userId={} provider={}", user.getId(), provider);
        writeLoginResult(response, result);
    }

    private UUID resolveTenantId(HttpServletRequest request) {
        Object attr = request.getAttribute(RedisOAuth2AuthorizationRequestRepository.REQUEST_ATTRIBUTE_NAME);
        if (attr instanceof OAuth2AuthorizationRequest authorizationRequest) {
            Object tenantIdParam = authorizationRequest.getAttributes().get(TENANT_ID_PARAM);
            if (tenantIdParam instanceof String tenantIdString && !tenantIdString.isBlank()) {
                try {
                    return UUID.fromString(tenantIdString);
                } catch (IllegalArgumentException e) {
                    log.warn("oauth.login.invalid_tenant_id value={}", tenantIdString);
                }
            }
        }
        return TenantConstants.PLATFORM_TENANT_ID;
    }

    private AuthUserEntity createPendingUser(UUID tenantId, String email) {
        AuthUserEntity user = AuthUserEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .email(email)
                .userType(UserType.TENANT_USER)
                .active(true)
                .build();
        return userRepo.save(user);
    }

    private void saveIdentity(String provider, String subject, String email, UUID userId) {
        identityRepo.save(AuthUserOAuthIdentityEntity.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .provider(provider)
                .providerSubject(subject)
                .email(email)
                .build());
    }

    private void writeLoginResult(HttpServletResponse response, LoginResult result) throws IOException {
        if (result.mfaChallenge() != null) {
            writeJson(response, new MfaLoginChallengeResponse(
                    true, result.mfaChallenge().challengeToken(), result.mfaChallenge().enrollmentRequired()));
            return;
        }
        if (behaviorProps.isJsonTokenDelivery()) {
            writeJson(response, result.response());
            return;
        }
        ResponseCookie accessCookie  = cookieFactory.createAccessTokenCookie(result.accessToken(), result.accessTokenTtl());
        ResponseCookie refreshCookie = cookieFactory.createRefreshTokenCookie(result.refreshToken(), result.refreshTokenTtl());
        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearLegacyRefreshTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieFactory.clearOldNarrowRefreshTokenCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        writeJson(response, result.response());
    }

    private void writeJson(HttpServletResponse response, Object body) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        OBJECT_MAPPER.writeValue(response.getWriter(), body);
    }
}
