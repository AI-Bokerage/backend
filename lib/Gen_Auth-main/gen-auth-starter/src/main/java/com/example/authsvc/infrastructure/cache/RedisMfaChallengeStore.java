package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.MfaChallengeEntry;
import com.example.authsvc.domain.port.MfaChallengeStore;
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
 * Redis-backed {@link MfaChallengeStore}.
 *
 * <p>Key schema: {@code mfa:challenge:<token>} → {@link MfaChallengeEntry} JSON
 * (TTL = configured challenge lifetime, 5 minutes per the design spec).
 *
 * <p>Registered as a {@code @Bean} in the MFA config class (Task 9) — not
 * annotated with {@code @Component} — so the {@code ObjectMapper} is
 * constructed with explicit {@code JavaTimeModule} settings, consistent with
 * {@link RedisMagicLinkStore} and {@code RedisRefreshTokenStore}.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisMfaChallengeStore implements MfaChallengeStore {

    static final String CHALLENGE_KEY_PREFIX = "mfa:challenge:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;
    private final SecureRandom        random = new SecureRandom();

    @Override
    public String issue(UUID userId, boolean enrollmentRequired, Duration ttl) {
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        MfaChallengeEntry entry = new MfaChallengeEntry(userId, enrollmentRequired, 0);
        String key = CHALLENGE_KEY_PREFIX + token;
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(entry), ttl);
        } catch (JsonProcessingException e) {
            log.error("mfa_challenge.redis.serialize_failed userId={} reason={}", userId, e.getMessage());
            throw new IllegalStateException("Failed to serialize MfaChallengeEntry", e);
        }
        log.debug("mfa_challenge.redis.issued userId={} ttlSeconds={}", userId, ttl.toSeconds());
        return token;
    }

    @Override
    public Optional<MfaChallengeEntry> find(String token) {
        String json = redisTemplate.opsForValue().get(CHALLENGE_KEY_PREFIX + token);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, MfaChallengeEntry.class));
        } catch (JsonProcessingException e) {
            log.error("mfa_challenge.redis.deserialize_failed reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void incrementAttempts(String token) {
        find(token).ifPresent(entry -> {
            MfaChallengeEntry updated = new MfaChallengeEntry(
                    entry.userId(), entry.enrollmentRequired(), entry.attempts() + 1);
            String key = CHALLENGE_KEY_PREFIX + token;
            Long ttl = redisTemplate.getExpire(key);
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(updated),
                        Duration.ofSeconds(ttl != null && ttl > 0 ? ttl : 1));
            } catch (JsonProcessingException e) {
                log.error("mfa_challenge.redis.serialize_failed reason={}", e.getMessage());
            }
        });
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(CHALLENGE_KEY_PREFIX + token);
    }
}
