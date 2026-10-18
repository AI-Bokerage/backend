package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.port.PermissionCacheStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Valkey/Redis-backed implementation of {@link PermissionCacheStore}.
 *
 * <p>Key schema (unified with ADM-SVC namespace, Phase 4+):
 * <pre>
 *   role:{roleId} → Redis SET of permission code strings
 * </pre>
 *
 * <p>This key namespace is written by ADM-SVC's {@code RbacPublisherService} at
 * bootstrap time and on every role permission update. AUTH-SVC reads the same keys,
 * eliminating the need for a separate auth:perms:{roleId} cache populated by events.
 *
 * <p>TTL: keys written by ADM have no TTL (persistent until ADM explicitly deletes
 * them). Keys written by {@link #putPermissions} carry a 5-minute TTL for safety.
 *
 * <p>Permission resolution for multi-role users: {@link #resolveAll(List)} unions
 * the permission sets of all assigned roles in a single pass.
 */
@Slf4j
@Component
@ConditionalOnBean(StringRedisTemplate.class)
@RequiredArgsConstructor
public class RedisPermissionCacheStore implements PermissionCacheStore {

    /** Unified with ADM-SVC's RbacPublisherService — both use "role:" prefix. */
    private static final String KEY_PREFIX  = "role:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;

    // ─── PermissionCacheStore Contract ───────────────────────────────────────

    @Override
    public Optional<Set<String>> getPermissions(UUID roleId) {
        Set<String> members = redisTemplate.opsForSet().members(KEY_PREFIX + roleId);
        if (members == null || members.isEmpty()) {
            log.debug("cache.miss type=permissions roleId={}", roleId);
            return Optional.empty();
        }
        log.debug("cache.hit type=permissions roleId={} count={}", roleId, members.size());
        return Optional.of(members);
    }

    /**
     * Unions permissions for every role in {@code roleIds}.
     *
     * <p>Each role is resolved via a separate Redis SMEMBERS call. For the typical
     * case of 1–3 roles per user this is fast enough without additional batching.
     * A pipelined implementation can be substituted later if latency becomes a concern.
     */
    @Override
    public Set<String> resolveAll(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            log.debug("cache.resolve_all.empty — no roleIds provided");
            return Set.of();
        }
        Set<String> union = new HashSet<>();
        for (UUID roleId : roleIds) {
            Set<String> members = redisTemplate.opsForSet().members(KEY_PREFIX + roleId);
            if (members != null) {
                union.addAll(members);
            }
        }
        log.debug("cache.resolve_all roleCount={} totalPermissions={}", roleIds.size(), union.size());
        return Set.copyOf(union);
    }

    @Override
    public void putPermissions(UUID roleId, Set<String> permissions) {
        String key = KEY_PREFIX + roleId;
        redisTemplate.delete(key);
        if (!permissions.isEmpty()) {
            redisTemplate.opsForSet().add(key, permissions.toArray(String[]::new));
            redisTemplate.expire(key, DEFAULT_TTL);
        }
        log.debug("cache.write type=permissions roleId={} count={}", roleId, permissions.size());
    }

    @Override
    public void evict(UUID roleId) {
        redisTemplate.delete(KEY_PREFIX + roleId);
        log.debug("cache.evict type=permissions roleId={}", roleId);
    }

    @Override
    public void evictAll() {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("cache.evict_all type=permissions count={}", keys.size());
        }
    }
}
