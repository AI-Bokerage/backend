package com.company.ppmsvc.planaddon.usecase;

import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planaddon.model.PlanAddOn;
import com.company.ppmsvc.planaddon.port.PlanAddOnRepositoryPort;
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
public class PlanAddOnApplicationServiceImpl implements PlanAddOnApplicationService {

    private final PlanRepositoryPort    planRepository;
    private final AddOnRepositoryPort   addOnRepository;
    private final PlanAddOnRepositoryPort planAddOnRepository;

    // ── assignAddOns ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void assignAddOns(UUID actorId, UUID planId, Set<UUID> addOnIds) {
        // BR-P1: plan must exist
        verifyPlanExists(planId);

        Instant now = Instant.now();
        List<PlanAddOn> toSave = new ArrayList<>();

        for (UUID addOnId : addOnIds) {
            // BR-P2: add-on must exist
            verifyAddOnExists(addOnId);

            // BR-P3: duplicate assignment rejected
            if (planAddOnRepository.exists(planId, addOnId)) {
                log.warn("plan_addon.duplicate planId={} addOnId={}", planId, addOnId);
                throw new BusinessException(ErrorCode.PLAN_ADD_ON_ALREADY_ASSIGNED,
                    "Add-on " + addOnId + " is already assigned to plan " + planId);
            }

            toSave.add(PlanAddOn.builder()
                .id(UUID.randomUUID())
                .planId(planId)
                .addOnId(addOnId)
                .createdAt(now)
                .createdBy(actorId)
                .build());
        }

        planAddOnRepository.saveAll(toSave);
        log.info("Assigned {} add-on(s) to plan {}", toSave.size(), planId);
    }

    // ── replaceAddOns ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void replaceAddOns(UUID actorId, UUID planId, Set<UUID> addOnIds) {
        // BR-P1: plan must exist
        verifyPlanExists(planId);

        // BR-P5, abort-before-delete: validate ALL add-ons before touching DB
        for (UUID addOnId : addOnIds) {
            verifyAddOnExists(addOnId);
        }

        // Atomic replace — single transaction
        planAddOnRepository.deleteAllByPlanId(planId);

        Instant now = Instant.now();
        List<PlanAddOn> newMappings = new ArrayList<>();
        for (UUID addOnId : addOnIds) {
            newMappings.add(PlanAddOn.builder()
                .id(UUID.randomUUID())
                .planId(planId)
                .addOnId(addOnId)
                .createdAt(now)
                .createdBy(actorId)
                .build());
        }
        planAddOnRepository.saveAll(newMappings);
        log.info("Replaced add-on assignments for plan {} with {} add-on(s)", planId, newMappings.size());
    }

    // ── getPlanAddOns ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PlanAddOn> getPlanAddOns(UUID planId) {
        log.debug("plan_addon.get planId={}", planId);
        verifyPlanExists(planId);
        return planAddOnRepository.findByPlanId(planId);
    }

    // ── removeAddOn ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void removeAddOn(UUID planId, UUID addOnId) {
        // BR-P6: plan must exist
        verifyPlanExists(planId);

        // BR-P8: mapping must exist before deletion
        if (!planAddOnRepository.exists(planId, addOnId)) {
            log.warn("plan_addon.mapping_not_found planId={} addOnId={}", planId, addOnId);
            throw new ResourceNotFoundException(ErrorCode.PLAN_ADD_ON_MAPPING_NOT_FOUND,
                "No mapping found for plan " + planId + " and add-on " + addOnId);
        }

        // BR-P7: only the join row is deleted — add-on itself is untouched
        planAddOnRepository.delete(planId, addOnId);
        log.info("Removed add-on {} from plan {}", addOnId, planId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void verifyPlanExists(UUID planId) {
        planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId));
    }

    private void verifyAddOnExists(UUID addOnId) {
        addOnRepository.findById(addOnId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.ADD_ON_NOT_FOUND, "Add-on not found: " + addOnId));
    }
}
