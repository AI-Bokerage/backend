package com.company.ppmsvc.planmodule.usecase;

import com.company.ppmsvc.module.model.Module;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for the Plan ↔ Module Mapping feature (PPM-03).
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface PlanModuleApplicationService {

    /**
     * Assigns the given set of modules to a plan.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: Plan must exist — {@code PLAN_NOT_FOUND}.</li>
     *   <li>BR-2: Every module in the set must exist — {@code MODULE_NOT_FOUND}.</li>
     *   <li>BR-3: Duplicate assignment is rejected — {@code MODULE_ALREADY_ASSIGNED_TO_PLAN}.</li>
     *   <li>BR-4: Duplicate IDs in {@code moduleIds} are already collapsed by {@link Set} semantics.</li>
     * </ul>
     *
     * @return the full list of modules now assigned to the plan.
     */
    List<Module> assignModules(UUID actorId, UUID planId, Set<UUID> moduleIds);

    /**
     * Returns all modules currently assigned to the plan.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if the plan does not exist.
     */
    List<Module> getModules(UUID planId);

    /**
     * Atomically replaces the complete set of modules assigned to a plan.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: Plan must exist — {@code PLAN_NOT_FOUND}.</li>
     *   <li>BR-2: Every module in the new set must exist — {@code MODULE_NOT_FOUND}.</li>
     *   <li>BR-5: The replace is atomic — old mappings removed and new ones inserted
     *       within a single transaction.</li>
     * </ul>
     *
     * @return the replacement list of assigned modules.
     */
    List<Module> replaceModules(UUID actorId, UUID planId, Set<UUID> moduleIds);

    /**
     * Removes a single module from a plan.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: Plan must exist — {@code PLAN_NOT_FOUND}.</li>
     *   <li>BR-7: Only the mapping row is deleted; the module itself is untouched.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_MODULE_MAPPING_NOT_FOUND} if no mapping exists for the pair.
     */
    void removeModule(UUID planId, UUID moduleId);
}
