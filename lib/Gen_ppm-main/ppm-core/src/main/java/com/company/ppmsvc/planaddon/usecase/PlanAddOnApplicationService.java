package com.company.ppmsvc.planaddon.usecase;

import com.company.ppmsvc.planaddon.model.PlanAddOn;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Primary port (use-case boundary) for the Plan ↔ Add-On assignment feature.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface PlanAddOnApplicationService {

    /**
     * Appends the given add-ons to a plan's assignments (BR-P1 through BR-P4).
     * Duplicate IDs in {@code addOnIds} are already deduplicated by {@link Set} semantics.
     * Throws {@code PLAN_ADD_ON_ALREADY_ASSIGNED} if any add-on is already assigned.
     */
    void assignAddOns(UUID actorId, UUID planId, Set<UUID> addOnIds);

    /**
     * Atomically replaces all current assignments for a plan with the given set
     * (BR-P5, abort-before-delete pattern).
     * All add-on IDs are validated before any delete is performed.
     */
    void replaceAddOns(UUID actorId, UUID planId, Set<UUID> addOnIds);

    /** Returns all current add-on assignments for the given plan. */
    List<PlanAddOn> getPlanAddOns(UUID planId);

    /**
     * Removes the assignment of {@code addOnId} from {@code planId} (BR-P7).
     * Throws {@code PLAN_ADD_ON_MAPPING_NOT_FOUND} if no such assignment exists.
     */
    void removeAddOn(UUID planId, UUID addOnId);
}
