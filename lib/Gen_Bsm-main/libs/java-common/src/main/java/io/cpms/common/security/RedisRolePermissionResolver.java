package io.cpms.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed {@link RolePermissionResolver}.
 *
 * <h3>Redis key structure</h3>
 * <pre>
 *   key:   role:{roleId}
 *   type:  Redis Set
 *   value: {"tenant.read", "tenant.write", "user.read", ...}
 *
 *   Example:
 *     SMEMBERS role:ad6c1fa5-71a6-4c93-9f40-b96de84c43f5
 *     → {"tenant.read", "tenant.write", "user.read", "user.write", ...}
 * </pre>
 *
 * <h3>Population</h3>
 * ADM-SVC's {@code RbacPublisherService} writes these keys:
 * <ul>
 *   <li>At startup via {@code RbacBootstrapRunner} — loads all roles from DB</li>
 *   <li>Immediately on role create / permission update via {@code RoleServiceImpl}</li>
 *   <li>On tenant bootstrap via {@code TenantBootstrapService}</li>
 * </ul>
 * Changes are visible to all services on the next request — no restart required.
 *
 * <h3>Failure behaviour</h3>
 * Returns empty set (fail-closed) on:
 * <ul>
 *   <li>Redis key missing (role not yet published)</li>
 *   <li>Redis unavailable (connection error)</li>
 *   <li>Null roleId</li>
 * </ul>
 */
public class RedisRolePermissionResolver implements RolePermissionResolver {

    private static final Logger log = LoggerFactory.getLogger(RedisRolePermissionResolver.class);
    static final String KEY_PREFIX = "role:";

    private final StringRedisTemplate redis;

    public RedisRolePermissionResolver(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Set<String> resolve(UUID roleId) {
        if (roleId == null) {
            log.debug("rbac.resolve.skip reason=null_roleId");
            return Collections.emptySet();
        }

        String key = KEY_PREFIX + roleId;
        try {
            Set<String> permissions = redis.opsForSet().members(key);
            if (permissions == null || permissions.isEmpty()) {
                log.warn("rbac.resolve.miss key={} — role not in Redis; denying access", key);
                return Collections.emptySet();
            }
            log.debug("rbac.resolve.hit key={} codes={}", key, permissions.size());
            return Collections.unmodifiableSet(permissions);
        } catch (Exception e) {
            log.error("rbac.resolve.error key={} reason={} — failing closed", key, e.getMessage());
            return Collections.emptySet();
        }
    }
}
