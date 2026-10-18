package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.OAuthSignupChallengeEntry;
import com.example.authsvc.domain.port.OAuthSignupChallengeStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed {@link OAuthSignupChallengeStore}.
 *
 * <p>Key schema: {@code oauth:signup:<token>} → {@link OAuthSignupChallengeEntry}
 * JSON (TTL = 10 minutes per the design spec). Implements the pattern of
 * Redis-backed, opaque-token-keyed challenge stores with automatic expiry.
 *
 * <p>Registered as a {@code @Bean} in {@code OAuthConfig} — not
 * annotated with {@code @Component} — so the {@code ObjectMapper} is
 * constructed with explicit {@code JavaTimeModule} settings to ensure
 * consistent serialization/deserialization of the entry records.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisOAuthSignupChallengeStore implements OAuthSignupChallengeStore {

    static final String CHALLENGE_KEY_PREFIX = "oauth:signup:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final SecureRandom        random = new SecureRandom();

    @Override
    public String issue(UUID userId, Duration ttl) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        OAuthSignupChallengeEntry entry = new OAuthSignupChallengeEntry(userId);
        String key = CHALLENGE_KEY_PREFIX + token;
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(entry), ttl);
        } catch (JsonProcessingException e) {
            log.error("oauth_signup_challenge.redis.serialize_failed userId={} reason={}", userId, e.getMessage());
            throw new IllegalStateException("Failed to serialize OAuthSignupChallengeEntry", e);
        }
        log.debug("oauth_signup_challenge.redis.issued userId={} ttlSeconds={}", userId, ttl.toSeconds());
        return token;
    }

    @Override
    public Optional<OAuthSignupChallengeEntry> find(String token) {
        String json = redisTemplate.opsForValue().get(CHALLENGE_KEY_PREFIX + token);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OAuthSignupChallengeEntry.class));
        } catch (JsonProcessingException e) {
            log.error("oauth_signup_challenge.redis.deserialize_failed reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(CHALLENGE_KEY_PREFIX + token);
    }
}
