package com.company.ppmsvc.addon.port;

import com.company.ppmsvc.addon.model.AddOn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port (secondary port) for {@link AddOn} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>All methods operate on the {@link AddOn} domain model — no JPA types
 * cross this boundary.
 */
public interface AddOnRepositoryPort {

    /**
     * Persists a new or updated add-on and returns the saved state.
     * The returned instance carries the DB-incremented {@code version}.
     */
    AddOn save(AddOn addOn);

    /** Returns the add-on with the given ID, or empty if not found (or soft-deleted). */
    Optional<AddOn> findById(UUID id);

    /** Returns the add-on with the given machine-readable code, or empty if not found. */
    Optional<AddOn> findByCode(String code);

    /** Returns {@code true} if an active add-on with the given code already exists. */
    boolean existsByCode(String code);

    /** Returns all non-deleted add-ons in the catalog, ordered by code ascending. */
    List<AddOn> findAll();

    /**
     * Soft-deletes the add-on with the given ID by setting its {@code deletedAt}
     * timestamp.  The add-on is invisible to all subsequent queries.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code ADD_ON_NOT_FOUND} if no active add-on with the given ID exists.
     */
    void softDelete(UUID id, UUID actorId);
}
