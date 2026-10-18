package com.company.ppmsvc.entitlement.port;

import com.company.ppmsvc.entitlement.model.Entitlement;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Output port (secondary port) for {@link Entitlement} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>All methods operate on the {@link Entitlement} domain model — no JPA
 * types cross this boundary.
 */
public interface EntitlementRepositoryPort {

    /**
     * Persists a new or updated entitlement and returns the saved state.
     * The returned instance carries the DB-incremented {@code version}.
     */
    Entitlement save(Entitlement entitlement);

    /** Returns the entitlement with the given ID, or empty if not found (or soft-deleted). */
    Optional<Entitlement> findById(UUID id);

    /** Returns the entitlement with the given code, or empty if not found (or soft-deleted). */
    Optional<Entitlement> findByCode(String code);

    /** Returns {@code true} if an active entitlement with the given code already exists. */
    boolean existsByCode(String code);

    /** Returns all active entitlements in the catalog, ordered by code ascending. */
    List<Entitlement> findAll();

    /**
     * Returns all entitlements whose IDs appear in the supplied list.
     * Used for batch loading by the resolver to avoid N+1 queries.
     * Soft-deleted entitlements are excluded (via {@code @SQLRestriction}).
     */
    List<Entitlement> findAllById(Set<UUID> ids);

    /**
     * Soft-deletes the entitlement with the given ID by setting its {@code deletedAt}
     * timestamp.  The entitlement is invisible to all subsequent queries.
     *
     * <p>Throws {@link com.company.ppmsvc.exception.ResourceNotFoundException}
     * if no active entitlement with the given ID exists.
     */
    void softDelete(UUID id, UUID actorId);
}
