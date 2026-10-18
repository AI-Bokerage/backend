package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.MagicLinkEntry;
import com.example.authsvc.domain.port.MagicLinkStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis-backed {@link MagicLinkStore}.
 *
 * <p>Key schema:
 * <pre>
 *   auth:magic:{tokenHash}          →  MagicLinkEntry JSON  (TTL = configured ttl)
 *   auth:rate:magic:issue:{key}     →  integer counter      (TTL = rate-limit window)
 * </pre>
 *
 * <p>Only the SHA-256 hash of the raw token is ever stored as a Redis key.
 * The raw token is sent to the user by email and never persisted.
 *
 * <p>Registered as a {@code @Bean} in
 * {@link com.example.authsvc.config.redis.RedisConfig} — not annotated with
 * {@code @Component} — so the ObjectMapper is constructed with explicit
 * JavaTimeModule / ISO-8601 settings, consistent with
 * {@link com.example.authsvc.infrastructure.cache.RedisRefreshTokenStore}.
 */
@Slf4j
@RequiredArgsConstructor
public class RedisMagicLinkStore implements MagicLinkStore {

    static final String MAGIC_KEY_PREFIX  = "auth:magic:";
    static final String RATE_LIMIT_PREFIX = "auth:rate:magic:issue:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    @Override
    public void save(String tokenHash, MagicLinkEntry entry, Duration ttl) {
        String key = MAGIC_KEY_PREFIX + tokenHash;
        String json;
        try {
            json = objectMapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            log.error("magic_link.redis.serialize_failed userId={} reason={}", entry.userId(), e.getMessage());
            throw new IllegalStateException("Failed to serialize MagicLinkEntry", e);
        }
        redisTemplate.opsForValue().set(key, json, ttl);
        log.debug("magic_link.redis.stored userId={} ttlSeconds={}", entry.userId(), ttl.toSeconds());
    }

    @Override
    public Optional<MagicLinkEntry> find(String tokenHash) {
        String json = redisTemplate.opsForValue().get(MAGIC_KEY_PREFIX + tokenHash);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, MagicLinkEntry.class));
        } catch (JsonProcessingException e) {
            log.error("magic_link.redis.deserialize_failed reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(String tokenHash) {
        redisTemplate.delete(MAGIC_KEY_PREFIX + tokenHash);
        log.debug("magic_link.redis.deleted");
    }

    @Override
    public long incrementRateCounter(String counterKey, Duration window) {
        String key   = RATE_LIMIT_PREFIX + counterKey;
        Long   count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, window);
        }
        return count == null ? 1L : count;
    }
}
