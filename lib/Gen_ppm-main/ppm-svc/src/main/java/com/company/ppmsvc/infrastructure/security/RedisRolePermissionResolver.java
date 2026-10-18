package com.company.ppmsvc.infrastructure.security;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves the effective permission set for a list of ADM role UUIDs by reading
 * the {@code role:{roleId}} Redis sets written by ADM-SVC's {@code RbacPublisherService}.
 *
 * <p>This class must NEVER write to or delete {@code role:*} keys — those are
 * owned exclusively by ADM-SVC.  PPM only reads them.
 *
 * <p>Fail-closed: if Redis is unavailable, returns an empty set causing
 * {@link PpmAccessAuthorizationFilter} to return 403.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRolePermissionResolver implements RolePermissionResolver {

    private static final String ROLE_KEY_PREFIX = "role:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public Set<String> resolve(UUID roleId) {
        if (roleId == null) return Collections.emptySet();
        return resolveAll(List.of(roleId));
    }

    public Set<String> resolveAll(List<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return Collections.emptySet();

        Set<String> union = new HashSet<>();
        for (UUID roleId : roleIds) {
            try {
                Set<String> perms = redisTemplate.opsForSet()
                    .members(ROLE_KEY_PREFIX + roleId);
                if (perms != null) union.addAll(perms);
            } catch (Exception e) {
                log.warn("Redis unavailable resolving role={}: {}", roleId, e.getMessage());
            }
        }
        return Collections.unmodifiableSet(union);
    }
}
