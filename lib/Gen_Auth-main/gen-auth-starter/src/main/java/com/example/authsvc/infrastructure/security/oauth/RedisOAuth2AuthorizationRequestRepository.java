package com.example.authsvc.infrastructure.security.oauth;

import com.example.authsvc.config.properties.CookieProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Redis-backed replacement for Spring Security's default
 * {@code HttpSessionOAuth2AuthorizationRequestRepository} — this service runs
 * {@code SessionCreationPolicy.STATELESS} (see {@code SecurityConfig}), so
 * there is no {@code HttpSession} to stash the in-flight
 * {@link OAuth2AuthorizationRequest} (which already carries Spring's
 * generated {@code state} and PKCE {@code code_verifier}) in.
 *
 * <p>Key schema: {@code oauth2:authreq:<state>} → JSON via Spring Security's
 * Jackson modules, 10-minute TTL, deleted on
 * {@link #removeAuthorizationRequest} (one-shot, prevents replay).
 *
 * <p>The stored entry also carries a hash of a random binding value delivered
 * to the browser via a short-lived {@code HttpOnly}/{@code SameSite=Lax}
 * cookie at save time. Load/remove require the caller's cookie to hash-match
 * the stored value — this binds the authorization request to the browser that
 * initiated it (the same protection Spring's session-backed default
 * repository gets for free via the session cookie), preventing a login-CSRF
 * where an attacker starts a flow and tricks a victim's browser into
 * completing it.
 *
 * <p>{@link #removeAuthorizationRequest} additionally stashes the removed
 * request on a request attribute ({@link #REQUEST_ATTRIBUTE_NAME}) — Spring
 * Security's {@code OAuth2LoginAuthenticationFilter} calls this method during
 * callback processing, before invoking the success handler, and the resulting
 * {@code OAuth2AuthenticationToken} the success handler receives carries no
 * reference back to the original authorization request. Reading this
 * attribute is how {@link OAuthLoginSuccessHandler} recovers the
 * {@code tenantId} that {@link TenantAwareOAuth2AuthorizationRequestResolver}
 * captured at {@code /oauth2/authorization/{registrationId}} time.
 */
@Slf4j
public class RedisOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String KEY_PREFIX = "oauth2:authreq:";
    public static final String REQUEST_ATTRIBUTE_NAME = "oauth2AuthorizationRequest";
    static final String BINDING_COOKIE_NAME = "oauth2_authreq_binding";
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String STATE_PARAM = "state";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final CookieProperties    cookieProperties;
    private final SecureRandom        random = new SecureRandom();

    public RedisOAuth2AuthorizationRequestRepository(StringRedisTemplate redisTemplate,
                                                     ObjectMapper objectMapper,
                                                     CookieProperties cookieProperties) {
        this.redisTemplate    = redisTemplate;
        this.objectMapper     = objectMapper;
        this.cookieProperties = cookieProperties;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter(STATE_PARAM);
        return state == null ? null : readIfBindingMatches(state, request);
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest,
                                          HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            String state = request.getParameter(STATE_PARAM);
            if (state != null) {
                redisTemplate.delete(KEY_PREFIX + state);
            }
            return;
        }

        byte[] bindingBytes = new byte[32];
        random.nextBytes(bindingBytes);
        String bindingValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bindingBytes);

        try {
            AuthorizationRequestEnvelope envelope =
                    new AuthorizationRequestEnvelope(authorizationRequest, hash(bindingValue));
            String json = objectMapper.writeValueAsString(envelope);
            redisTemplate.opsForValue().set(KEY_PREFIX + authorizationRequest.getState(), json, TTL);
        } catch (JsonProcessingException e) {
            log.error("oauth2.authreq.redis.serialize_failed reason={}", e.getMessage());
            throw new IllegalStateException("Failed to serialize OAuth2AuthorizationRequest", e);
        }

        ResponseCookie bindingCookie = ResponseCookie.from(BINDING_COOKIE_NAME, bindingValue)
                .httpOnly(true)
                .secure(cookieProperties.isSecure())
                .sameSite("Lax")
                .path("/")
                .domain(cookieProperties.getDomain())
                .maxAge(TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, bindingCookie.toString());
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, HttpServletResponse response) {
        String state = request.getParameter(STATE_PARAM);
        if (state == null) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = readIfBindingMatches(state, request);
        if (authorizationRequest != null) {
            redisTemplate.delete(KEY_PREFIX + state);
            request.setAttribute(REQUEST_ATTRIBUTE_NAME, authorizationRequest);
        }
        return authorizationRequest;
    }

    private OAuth2AuthorizationRequest readIfBindingMatches(String state, HttpServletRequest request) {
        AuthorizationRequestEnvelope envelope = readEnvelope(state);
        if (envelope == null) {
            return null;
        }
        String cookieValue = readCookie(request, BINDING_COOKIE_NAME);
        if (cookieValue == null || !hash(cookieValue).equals(envelope.bindingHash())) {
            log.warn("oauth2.authreq.binding_mismatch state={}", state);
            return null;
        }
        return envelope.request();
    }

    private AuthorizationRequestEnvelope readEnvelope(String state) {
        String json = redisTemplate.opsForValue().get(KEY_PREFIX + state);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AuthorizationRequestEnvelope.class);
        } catch (JsonProcessingException e) {
            log.error("oauth2.authreq.redis.deserialize_failed reason={}", e.getMessage());
            return null;
        }
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private record AuthorizationRequestEnvelope(OAuth2AuthorizationRequest request, String bindingHash) {}
}
