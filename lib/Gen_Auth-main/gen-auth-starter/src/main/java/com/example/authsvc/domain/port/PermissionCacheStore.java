package com.example.authsvc.domain.port;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Domain port for caching per-role permission sets.
 *
 * <p>Avoids repeated database reads for permission resolution on hot paths.
 * Implementations should use Caffeine (L1) and/or Valkey/Redis (L2).
 */
public interface PermissionCacheStore {

    /**
     * Returns the cached set of permission codes for the given role, or
     * {@code Optional.empty()} if the cache does not contain an entry.
     *
     * @param roleId The role whose permissions to look up.
     */
    Optional<Set<String>> getPermissions(UUID roleId);

    /**
     * Stores the permission set for the given role in the cache.
     *
     * @param roleId      The role to cache permissions for.
     * @param permissions Set of permission code strings.
     */
    void putPermissions(UUID roleId, Set<String> permissions);

    /**
     * Evicts the cached permissions for the given role (e.g. after a role update).
     *
     * @param roleId The role whose cache entry should be invalidated.
     */
    void evict(UUID roleId);

    /** Evicts all cached permission entries. */
    void evictAll();

    /**
     * Resolves and unions permissions for every role in {@code roleIds}.
     * Equivalent to calling {@link #getPermissions(UUID)} for each ID and
     * collecting the union into a single set — but implementations may optimise
     * this into a single pipelined Redis call.
     *
     * @param roleIds the roles whose permissions should be unioned
     * @return deduplicated set of permission codes; empty if no roles or no cache hits
     */
    Set<String> resolveAll(List<UUID> roleIds);
}
