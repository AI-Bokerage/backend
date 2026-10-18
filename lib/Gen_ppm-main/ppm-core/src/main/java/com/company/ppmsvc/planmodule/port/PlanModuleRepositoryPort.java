package com.company.ppmsvc.planmodule.port;

import com.company.ppmsvc.planmodule.model.PlanModule;
import java.util.List;
import java.util.UUID;

/**
 * Output port (secondary port) for {@link PlanModule} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>All methods operate on the {@link PlanModule} domain model — no JPA
 * types cross this boundary.
 */
public interface PlanModuleRepositoryPort {

    /** Persists a new mapping and returns the saved state. */
    PlanModule save(PlanModule mapping);

    /**
     * Persists a batch of new mappings in a single database round-trip.
     * The returned list preserves insertion order.
     */
    List<PlanModule> saveAll(List<PlanModule> mappings);

    /** Returns all mappings for the given plan, in insertion order. */
    List<PlanModule> findByPlanId(UUID planId);

    /** Returns all mappings that reference the given module. */
    List<PlanModule> findByModuleId(UUID moduleId);

    /** Returns {@code true} if a mapping for (planId, moduleId) already exists. */
    boolean exists(UUID planId, UUID moduleId);

    /**
     * Hard-deletes the mapping for (planId, moduleId).
     * A no-op if no such mapping exists.
     */
    void delete(UUID planId, UUID moduleId);

    /**
     * Hard-deletes all mappings for the given plan.
     * Used by the atomic replace operation.
     */
    void deleteAllByPlanId(UUID planId);
}
