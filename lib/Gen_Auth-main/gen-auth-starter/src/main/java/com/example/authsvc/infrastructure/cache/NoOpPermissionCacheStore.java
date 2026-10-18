package com.example.authsvc.infrastructure.cache;

import com.example.authsvc.domain.port.PermissionCacheStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Fallback no-op implementation used when Redis / Valkey is unavailable.
 * Always reports a cache miss so callers fall back to empty permissions.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class NoOpPermissionCacheStore implements PermissionCacheStore {

    @Override
    public Optional<Set<String>> getPermissions(UUID roleId) {
        log.debug("permission_cache.no_op.miss roleId={} — Redis unavailable", roleId);
        return Optional.empty();
    }

    @Override
    public Set<String> resolveAll(List<UUID> roleIds) {
        log.debug("permission_cache.no_op.resolve_all — Redis unavailable, roleCount={}", roleIds.size());
        return Collections.emptySet();
    }

    @Override
    public void putPermissions(UUID roleId, Set<String> permissions) {}

    @Override
    public void evict(UUID roleId) {}

    @Override
    public void evictAll() {}
}
