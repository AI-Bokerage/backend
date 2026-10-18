package com.company.ppmsvc.planentitlement.usecase;

import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planentitlement.model.PlanEntitlement;
import com.company.ppmsvc.planentitlement.model.ResolvedEntitlementResponse;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanEntitlementApplicationServiceImpl implements PlanEntitlementApplicationService {

    private final PlanRepositoryPort            planRepository;
    private final EntitlementRepositoryPort     entitlementRepository;
    private final PlanEntitlementRepositoryPort planEntitlementRepository;
    private final EntitlementResolver           resolver;

    // ── assignEntitlements ────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<ResolvedEntitlementResponse> assignEntitlements(
            UUID actorId, UUID planId, Set<UUID> entitlementIds) {

        // BR-P1: plan must exist
        verifyPlanExists(planId);

        Instant now = Instant.now();
        List<PlanEntitlement> toSave = new ArrayList<>();

        for (UUID entitlementId : entitlementIds) {
            // BR-P2: entitlement must exist
            Entitlement entitlement = findEntitlementOrThrow(entitlementId);

            // BR-P3: duplicate assignment rejected
            if (planEntitlementRepository.exists(planId, entitlementId)) {
                log.warn("plan_entitlement.duplicate planId={} entitlementId={}", planId, entitlementId);
                throw new BusinessException(ErrorCode.PLAN_ENTITLEMENT_ALREADY_ASSIGNED,
                    "Entitlement '" + entitlement.getCode() + "' is already assigned to plan " + planId);
            }

            toSave.add(PlanEntitlement.builder()
                .id(UUID.randomUUID())
                .planId(planId)
                .entitlementId(entitlementId)
                .value(defaultValue(entitlement))
                .createdAt(now)
                .createdBy(actorId)
                .build());
        }

        planEntitlementRepository.saveAll(toSave);
        log.info("Assigned {} entitlement(s) to plan {}", toSave.size(), planId);

        return resolver.resolveEntitlements(planId);
    }

    // ── replaceEntitlements ───────────────────────────────────────────────────

    @Override
    @Transactional
    public List<ResolvedEntitlementResponse> replaceEntitlements(
            UUID actorId, UUID planId, Set<UUID> entitlementIds) {

        // BR-P1: plan must exist
        verifyPlanExists(planId);

        // BR-P2: validate ALL entitlements exist before touching the DB (abort-before-delete)
        List<Entitlement> entitlements = new ArrayList<>();
        for (UUID entitlementId : entitlementIds) {
            entitlements.add(findEntitlementOrThrow(entitlementId));
        }

        // BR-P5: atomic replace — delete then insert in the same transaction
        planEntitlementRepository.deleteAllByPlanId(planId);

        Instant now = Instant.now();
        List<PlanEntitlement> newAssignments = new ArrayList<>();
        for (Entitlement entitlement : entitlements) {
            newAssignments.add(PlanEntitlement.builder()
                .id(UUID.randomUUID())
                .planId(planId)
                .entitlementId(entitlement.getId())
                .value(defaultValue(entitlement))
                .createdAt(now)
                .createdBy(actorId)
                .build());
        }
        planEntitlementRepository.saveAll(newAssignments);
        log.info("Replaced entitlement assignments for plan {} with {} entitlement(s)",
            planId, newAssignments.size());

        return resolver.resolveEntitlements(planId);
    }

    // ── getPlanEntitlements ───────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ResolvedEntitlementResponse> getPlanEntitlements(UUID planId) {
        log.debug("plan_entitlement.get planId={}", planId);
        verifyPlanExists(planId);
        return resolver.resolveEntitlements(planId);
    }

    // ── removeEntitlement ─────────────────────────────────────────────────────

    @Override
    @Transactional
    public void removeEntitlement(UUID planId, UUID entitlementId) {
        // BR-P1: plan must exist
        verifyPlanExists(planId);

        // BR-P7: verify the mapping exists before deleting
        if (!planEntitlementRepository.exists(planId, entitlementId)) {
            log.warn("plan_entitlement.mapping_not_found planId={} entitlementId={}", planId, entitlementId);
            throw new ResourceNotFoundException(ErrorCode.PLAN_ENTITLEMENT_MAPPING_NOT_FOUND,
                "No mapping found for plan " + planId + " and entitlement " + entitlementId);
        }

        // BR-P6: only the mapping row is deleted — entitlement catalog entry is untouched
        planEntitlementRepository.delete(planId, entitlementId);
        log.info("Removed entitlement {} from plan {}", entitlementId, planId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void verifyPlanExists(UUID planId) {
        planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId));
    }

    private Entitlement findEntitlementOrThrow(UUID entitlementId) {
        return entitlementRepository.findById(entitlementId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ENTITLEMENT_NOT_FOUND, "Entitlement not found: " + entitlementId));
    }

    /**
     * Returns the initial value to use when assigning an entitlement to a plan.
     *
     * <p>Phase 2 uses a sensible default per type; the value can be overridden
     * once an update operation is introduced in a future phase.
     * <ul>
     *   <li>BOOLEAN — {@code "false"} (opt-in by default)</li>
     *   <li>QUOTA / RATE_LIMIT — {@code "0"} (unconfigured; must be set explicitly)</li>
     * </ul>
     */
    private static String defaultValue(Entitlement entitlement) {
        return switch (entitlement.getType()) {
            case BOOLEAN    -> "false";
            case QUOTA, RATE_LIMIT -> "0";
        };
    }
}
