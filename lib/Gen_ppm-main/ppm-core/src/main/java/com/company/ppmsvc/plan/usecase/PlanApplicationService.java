package com.company.ppmsvc.plan.usecase;

import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVersion;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Plan Catalog.
 *
 * <p>Framework-agnostic business use-case boundary — operates exclusively on
 * domain models and primitives. No REST, DTO, or transport-specific types
 * cross this interface; a host application (e.g. {@code ppm-svc}) is
 * responsible for translating its own request/response contracts to and
 * from these signatures.
 *
 * <p>Slug policy: slugs are immutable after creation. {@link #updatePlan}
 * does not accept a slug parameter. Callers wishing to "change" a slug must
 * soft-delete the plan and recreate it.
 */
public interface PlanApplicationService {

    /**
     * Creates a new subscription plan.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: {@code code} must be unique across all active (non-deleted) plans.</li>
     *   <li>BR-2: {@code slug} is system-generated via {@code ppm_plan_slug_seq} in the
     *       form {@code PLN-0001}. Callers must not supply a slug.</li>
     *   <li>BR-3: {@code trialDays} defaults to {@code 0} when {@code null}.</li>
     *   <li>BR-4: {@code active} defaults to {@code true} when {@code null}.</li>
     *   <li>BR-5: {@code createdBy} / {@code updatedBy} are set to {@code actorId}.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code PLAN_CODE_ALREADY_EXISTS} if the code is already in use.
     */
    Plan createPlan(UUID actorId, String code, String name, String tagline, String description,
                     PlanVisibility visibility, Integer trialDays, Boolean active, String tier);

    /**
     * Partially updates an existing plan.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: Plan must exist and must not be soft-deleted.</li>
     *   <li>BR-2: {@code name}, {@code tagline}, {@code description}, {@code visibility},
     *             {@code trialDays}, and {@code active} are optional;
     *             a {@code null} value means "leave unchanged".</li>
     *   <li>BR-3: If {@code name} is supplied it must not be blank.</li>
     *   <li>BR-4: {@code code} is immutable — preserved regardless of input.</li>
     *   <li>BR-5: {@code slug} is immutable — preserved regardless of input.</li>
     *   <li>BR-6: {@code updatedAt} and {@code updatedBy} are refreshed.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if the plan does not exist or is soft-deleted.
     */
    Plan updatePlan(UUID actorId, UUID planId, String name, String tagline, String description,
                     PlanVisibility visibility, Integer trialDays, Boolean active, String tier);

    /**
     * Returns a single plan by its ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if the plan does not exist or is soft-deleted.
     */
    Plan getPlan(UUID planId);

    /**
     * Returns a single plan by its slug.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if no active plan has the given slug.
     */
    Plan getPlanBySlug(String slug);

    /**
     * Returns all non-deleted plans matching the supplied filters, ordered by
     * code ascending. A {@code null} filter value means "no filter".
     */
    List<Plan> listPlans(Boolean active, PlanVisibility visibility);

    /**
     * Soft-deletes an existing plan.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if the plan does not exist or is already soft-deleted.
     */
    void deletePlan(UUID actorId, UUID planId);

    /**
     * Returns the default trial plan and its latest active version.
     *
     * <p>The default trial plan is the first active plan whose {@code tier} is
     * {@code "trial"} (case-insensitive), ordered by code ascending.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code TRIAL_PLAN_NOT_FOUND} if no active plan with tier {@code "trial"}
     *         exists, or if such a plan exists but has no active version.
     */
    PlanWithActiveVersion getDefaultTrialPlan();

    /**
     * Returns the plan matching the given code and its latest active version.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PLAN_NOT_FOUND} if no active plan with the given code exists,
     *         or if the plan exists but has no active version.
     */
    PlanWithActiveVersion getPlanByCode(String code);

    /** A plan paired with its currently active version — used by trial/code lookups. */
    record PlanWithActiveVersion(Plan plan, PlanVersion activeVersion) {}
}
