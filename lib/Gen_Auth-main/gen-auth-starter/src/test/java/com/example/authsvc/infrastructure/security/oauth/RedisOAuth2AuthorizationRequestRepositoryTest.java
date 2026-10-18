package com.example.authsvc.infrastructure.security.oauth;

import com.example.authsvc.config.properties.CookieProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RedisOAuth2AuthorizationRequestRepositoryTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private AuthorizationRequestRepository<OAuth2AuthorizationRequest> repository;
    private final Map<String, String> fakeRedis = new HashMap<>();

    @SuppressWarnings("removal")  // see OAuthConfig — jackson2 variant until the Jackson 3 migration
    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        repository = new RedisOAuth2AuthorizationRequestRepository(redisTemplate, mapper, new CookieProperties());

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().doAnswer(inv -> fakeRedis.put(inv.getArgument(0), inv.getArgument(1)))
                .when(valueOps).set(anyString(), anyString(), any(java.time.Duration.class));
        lenient().when(valueOps.get(any())).thenAnswer(inv -> fakeRedis.get((String) inv.getArgument(0)));
        lenient().doAnswer(inv -> fakeRedis.remove(inv.getArgument(0)) != null)
                .when(redisTemplate).delete(anyString());
    }

    /** Mirrors what DefaultOAuth2AuthorizationRequestResolver actually builds. */
    private OAuth2AuthorizationRequest testRequest(String state) {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("test-client-id")
                .redirectUri("https://app.example.com/login/oauth2/code/google")
                .scopes(Set.of("openid", "email"))
                .state(state)
                .attributes(attrs -> {
                    attrs.put("registration_id", "google");
                    attrs.put("nonce", "test-nonce-value");
                    attrs.put("code_verifier", "test-code-verifier-value");
                })
                .additionalParameters(params -> {
                    params.put("code_challenge", "test-code-challenge-value");
                    params.put("code_challenge_method", "S256");
                })
                .build();
    }

    /** Replays the binding cookie the repository set during save onto a follow-up request. */
    private void replayBindingCookie(MockHttpServletResponse saveResponse, MockHttpServletRequest nextRequest) {
        Cookie binding = saveResponse.getCookie(RedisOAuth2AuthorizationRequestRepository.BINDING_COOKIE_NAME);
        assertThat(binding).isNotNull();
        assertThat(binding.getValue()).isNotBlank();
        nextRequest.setCookies(new Cookie(
                RedisOAuth2AuthorizationRequestRepository.BINDING_COOKIE_NAME, binding.getValue()));
    }

    @Test
    void saveThenLoad_roundTrips() {
        OAuth2AuthorizationRequest original = testRequest("state-abc");
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();

        repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setParameter("state", "state-abc");
        replayBindingCookie(saveResponse, loadRequest);

        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        assertThat(loaded).isNotNull();
        assertThat(loaded.getState()).isEqualTo("state-abc");
        assertThat(loaded.getClientId()).isEqualTo("test-client-id");
        assertThat(loaded.getAttributes())
                .containsEntry("registration_id", "google")
                .containsEntry("nonce", "test-nonce-value")
                .containsEntry("code_verifier", "test-code-verifier-value");
        assertThat(loaded.getAdditionalParameters())
                .containsEntry("code_challenge", "test-code-challenge-value")
                .containsEntry("code_challenge_method", "S256");
    }

    @Test
    void loadAuthorizationRequest_unknownState_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", "never-saved");

        assertThat(repository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void removeAuthorizationRequest_deletesFromRedisAndStashesOnRequestAttribute() {
        OAuth2AuthorizationRequest original = testRequest("state-xyz");
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        repository.saveAuthorizationRequest(original, new MockHttpServletRequest(), saveResponse);

        MockHttpServletRequest removeRequest = new MockHttpServletRequest();
        removeRequest.setParameter("state", "state-xyz");
        replayBindingCookie(saveResponse, removeRequest);

        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(removeRequest, new MockHttpServletResponse());

        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("state-xyz");
        assertThat(removeRequest.getAttribute(RedisOAuth2AuthorizationRequestRepository.REQUEST_ATTRIBUTE_NAME))
                .isSameAs(removed);
        assertThat(fakeRedis).doesNotContainKey("oauth2:authreq:state-xyz");
    }

    @Test
    void removeAuthorizationRequest_unknownState_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("state", "never-saved");

        assertThat(repository.removeAuthorizationRequest(request, new MockHttpServletResponse())).isNull();
    }

    @Test
    void load_withoutBindingCookie_returnsNull() {
        repository.saveAuthorizationRequest(testRequest("state-nocookie"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setParameter("state", "state-nocookie");

        assertThat(repository.loadAuthorizationRequest(loadRequest)).isNull();
        assertThat(fakeRedis).containsKey("oauth2:authreq:state-nocookie");
    }

    @Test
    void remove_withWrongBindingCookie_returnsNullAndLeavesEntryForLegitimateRetry() {
        repository.saveAuthorizationRequest(testRequest("state-wrongcookie"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        MockHttpServletRequest removeRequest = new MockHttpServletRequest();
        removeRequest.setParameter("state", "state-wrongcookie");
        removeRequest.setCookies(new Cookie(
                RedisOAuth2AuthorizationRequestRepository.BINDING_COOKIE_NAME, "some-other-browsers-value"));

        assertThat(repository.removeAuthorizationRequest(removeRequest, new MockHttpServletResponse())).isNull();
        assertThat(removeRequest.getAttribute(RedisOAuth2AuthorizationRequestRepository.REQUEST_ATTRIBUTE_NAME)).isNull();
        assertThat(fakeRedis).containsKey("oauth2:authreq:state-wrongcookie");
    }
}
