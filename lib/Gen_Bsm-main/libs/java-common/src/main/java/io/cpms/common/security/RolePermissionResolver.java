package io.cpms.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves the permission code set for a role at request time.
 *
 * <p>The single implementation is {@link RedisRolePermissionResolver} which reads
 * from Redis key {@code role:{roleId}} — a Set of permission code strings published
 * by ADM-SVC whenever role permissions are created or changed.
 *
 * <p>Contract:
 * <ul>
 *   <li>Returns the live permission set for the role if the key exists in Redis.</li>
 *   <li>Returns an <em>empty set</em> (fail-closed) if the key does not exist or Redis
 *       is unreachable. The caller must deny access on an empty result.</li>
 *   <li>Never performs a DB query — Redis is the sole runtime source of truth.</li>
 * </ul>
 */
public interface RolePermissionResolver {

    /**
     * @param roleId the role UUID extracted from the JWT {@code role_id} claim
     * @return immutable set of permission codes; empty if unresolvable
     */
    Set<String> resolve(UUID roleId);
}
