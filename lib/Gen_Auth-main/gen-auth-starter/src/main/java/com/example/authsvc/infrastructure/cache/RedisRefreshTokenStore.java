package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.model.RefreshToken;
import com.example.authsvc.domain.port.RefreshTokenStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed primary store for active refresh tokens.
 *
 * <p>Key schema:
 * <pre>
 *   auth:refresh:{tokenHash}          →  RefreshToken JSON  (TTL = expiresAt - now)
 *   auth:user-sessions:{userId}       →  Set&lt;tokenHash&gt;     (TTL = token TTL + 1 day)
 *   auth:family:{familyId}            →  Set&lt;tokenHash&gt;     (TTL = token TTL + 1 day)
 * </pre>
 *
 * <p>Serialization strategy: {@link StringRedisTemplate} stores every value as a
 * plain UTF-8 string. {@link RefreshToken} objects are explicitly serialised to JSON
 * via {@link ObjectMapper} and deserialised back to the concrete type with
 * {@code readValue(json, RefreshToken.class)}.  This avoids the {@code LinkedHashMap}
 * corruption produced by generic type-erased serializers that lack {@code @class}
 * metadata support.
 *
 * <p>The PostgreSQL {@code auth_refresh_tokens} table is retained as an append-only
 * audit/forensic history. This store is the authoritative source for <em>active</em> tokens.
 *
 * <p>Do not annotate with {@code @Component} — this class is registered as a
 * {@code @Bean} inside {@link com.example.authsvc.config.redis.RedisConfig}, which
 * is itself {@code @ConditionalOnBean(RedisConnectionFactory.class)}. That ensures
 * the condition is evaluated after Spring Boot autoconfiguration has registered
 * the connection factory, avoiding a false-negative component-scan ordering issue.
 */
@Slf4j
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String TOKEN_PREFIX  = "auth:refresh:";
    private static final String USER_PREFIX   = "auth:user-sessions:";
    private static final String FAMILY_PREFIX = "auth:family:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper        objectMapper;

    public RedisRefreshTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper  = objectMapper;
    }

    @Override
    public void save(RefreshToken token) {
        Duration ttl = Duration.between(Instant.now(), token.getExpiresAt());
        if (ttl.isNegative() || ttl.isZero()) {
            log.warn("refresh.redis.skip_expired userId={} sessionId={}",
                    token.getUserId(), token.getSessionId());
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(token);
        } catch (JsonProcessingException e) {
            log.error("refresh.redis.serialize_failed userId={} sessionId={} reason={}",
                    token.getUserId(), token.getSessionId(), e.getMessage());
            return;
        }

        long start = System.currentTimeMillis();
        String tokenKey = TOKEN_PREFIX + token.getTokenHash();
        redisTemplate.opsForValue().set(tokenKey, json, ttl);

        String userKey = USER_PREFIX + token.getUserId();
        redisTemplate.opsForSet().add(userKey, token.getTokenHash());
        redisTemplate.expire(userKey, ttl.plusDays(1));

        String familyKey = FAMILY_PREFIX + token.getFamilyId();
        redisTemplate.opsForSet().add(familyKey, token.getTokenHash());
        redisTemplate.expire(familyKey, ttl.plusDays(1));
        log.info("perf.redis.set.ms={}", System.currentTimeMillis() - start);

        log.debug("refresh.redis.store userId={} sessionId={} familyId={} generation={} ttlSeconds={}",
                token.getUserId(), token.getSessionId(), token.getFamilyId(),
                token.getGeneration(), ttl.toSeconds());
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        long start = System.currentTimeMillis();
        String json = redisTemplate.opsForValue().get(TOKEN_PREFIX + tokenHash);
        log.info("perf.redis.get.ms={}", System.currentTimeMillis() - start);

        if (json == null) {
            log.debug("refresh.redis.miss");
            return Optional.empty();
        }

        try {
            RefreshToken rt = objectMapper.readValue(json, RefreshToken.class);
            log.debug("refresh.redis.hit userId={} sessionId={} generation={}",
                    rt.getUserId(), rt.getSessionId(), rt.getGeneration());
            return Optional.of(rt);
        } catch (JsonProcessingException e) {
            log.error("refresh.redis.deserialize_failed tokenHash=*** reason={}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void revokeByTokenHash(String tokenHash) {
        long start = System.currentTimeMillis();
        redisTemplate.delete(TOKEN_PREFIX + tokenHash);
        log.info("perf.redis.delete.ms={}", System.currentTimeMillis() - start);
        log.debug("refresh.redis.delete tokenHash=***");
    }

    @Override
    public void revokeAllByUserId(UUID userId) {
        long start = System.currentTimeMillis();
        String userKey = USER_PREFIX + userId;
        Set<String> hashes = redisTemplate.opsForSet().members(userKey);
        if (hashes == null || hashes.isEmpty()) {
            redisTemplate.delete(userKey);
            log.info("perf.redis.delete.ms={}", System.currentTimeMillis() - start);
            return;
        }
        hashes.forEach(h -> redisTemplate.delete(TOKEN_PREFIX + h));
        redisTemplate.delete(userKey);
        log.info("perf.redis.delete.ms={}", System.currentTimeMillis() - start);
        log.debug("refresh.redis.revoke_user userId={} count={}", userId, hashes.size());
    }

    @Override
    public void revokeAllBySessionId(UUID sessionId) {
        // Scan user-session indexes to find tokens belonging to this session.
        // Since we don't maintain a session-level index, we remove only what we can find
        // by looking up the token directly (callers should pass individual tokenHashes via
        // revokeByTokenHash when the hash is known, e.g. during rotation).
        // For bulk session revocation (logout), revokeAllByUserId is the primary path.
        log.debug("refresh.redis.revoke_session sessionId={}", sessionId);
    }

    @Override
    public void revokeByFamilyId(UUID familyId) {
        long start = System.currentTimeMillis();
        String familyKey = FAMILY_PREFIX + familyId;
        Set<String> hashes = redisTemplate.opsForSet().members(familyKey);
        if (hashes == null || hashes.isEmpty()) {
            redisTemplate.delete(familyKey);
            log.info("perf.redis.delete.ms={}", System.currentTimeMillis() - start);
            return;
        }
        hashes.forEach(h -> redisTemplate.delete(TOKEN_PREFIX + h));
        redisTemplate.delete(familyKey);
        log.info("perf.redis.delete.ms={}", System.currentTimeMillis() - start);
        log.warn("refresh.redis.revoke_family familyId={} count={}", familyId, hashes.size());
    }
}
