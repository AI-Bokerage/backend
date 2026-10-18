package com.company.ppmsvc.module.port;

import com.company.ppmsvc.module.model.ModuleCode;
import com.company.ppmsvc.module.model.Module;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Output port (secondary port) for {@link Module} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>All methods operate on the {@link Module} domain model — no JPA types
 * cross this boundary.
 */
public interface ModuleRepositoryPort {

    /**
     * Persists a new or updated module and returns the saved state.
     * The returned instance carries the DB-incremented {@code version}.
     */
    Module save(Module module);

    /** Returns the module with the given ID, or empty if not found. */
    Optional<Module> findById(UUID id);

    /** Returns the module with the given canonical code, or empty if not found. */
    Optional<Module> findByCode(ModuleCode code);

    /** Returns {@code true} if a module with the given code already exists. */
    boolean existsByCode(ModuleCode code);

    /** Returns all modules in the catalog, ordered by code ascending. */
    List<Module> findAll();

    /**
     * Soft-deletes the module with the given ID by setting its {@code deletedAt}
     * timestamp.  The module is invisible to all subsequent queries.
     *
     * <p>Throws {@link com.company.ppmsvc.exception.ResourceNotFoundException}
     * if no active module with the given ID exists.
     */
    void softDelete(UUID id, UUID actorId);
}
