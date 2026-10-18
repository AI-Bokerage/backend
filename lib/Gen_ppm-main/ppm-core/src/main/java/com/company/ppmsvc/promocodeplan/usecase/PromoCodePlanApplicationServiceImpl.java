package com.company.ppmsvc.promocodeplan.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
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
public class PromoCodePlanApplicationServiceImpl implements PromoCodePlanApplicationService {

    private final PromoCodeRepositoryPort     promoCodeRepository;
    private final PlanRepositoryPort          planRepository;
    private final PromoCodePlanRepositoryPort promoCodePlanRepository;

    // ── assignPlans ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<PromoCodePlan> assignPlans(UUID actorId, UUID promoCodeId, Set<UUID> planIds) {
        verifyPromoCodeExists(promoCodeId);

        Instant now = Instant.now();
        List<PromoCodePlan> toSave = new ArrayList<>();

        for (UUID planId : planIds) {
            // BR-P1: plan must exist
            verifyPlanExists(planId);

            // BR-P2: duplicate assignment rejected
            if (promoCodePlanRepository.exists(promoCodeId, planId)) {
                log.warn("promo_plan.duplicate promoCodeId={} planId={}", promoCodeId, planId);
                throw new BusinessException(ErrorCode.PROMO_CODE_PLAN_ALREADY_ASSIGNED,
                    "Promo code " + promoCodeId + " is already restricted to plan " + planId);
            }

            toSave.add(PromoCodePlan.builder()
                .id(UUID.randomUUID())
                .promoCodeId(promoCodeId)
                .planId(planId)
                .createdAt(now)
                .createdBy(actorId)
                .build());
        }

        List<PromoCodePlan> saved = promoCodePlanRepository.saveAll(toSave);
        log.info("Assigned {} plan restriction(s) to promo code {}", saved.size(), promoCodeId);
        return saved;
    }

    // ── replacePlans ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<PromoCodePlan> replacePlans(UUID actorId, UUID promoCodeId, Set<UUID> planIds) {
        verifyPromoCodeExists(promoCodeId);

        // BR-P4: validate ALL plans first — abort before any delete if any plan is invalid
        Instant now = Instant.now();
        List<PromoCodePlan> validated = new ArrayList<>();

        for (UUID planId : planIds) {
            verifyPlanExists(planId);
            validated.add(PromoCodePlan.builder()
                .id(UUID.randomUUID())
                .promoCodeId(promoCodeId)
                .planId(planId)
                .createdAt(now)
                .createdBy(actorId)
                .build());
        }

        // Only after all validations pass: atomic delete-then-insert
        promoCodePlanRepository.deleteAllByPromoCodeId(promoCodeId);
        List<PromoCodePlan> saved = promoCodePlanRepository.saveAll(validated);
        log.info("Replaced plan restrictions for promo code {} with {} plan(s)", promoCodeId, saved.size());
        return saved;
    }

    // ── getRestrictedPlans ────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PromoCodePlan> getRestrictedPlans(UUID promoCodeId) {
        log.debug("promo_plan.get promoCodeId={}", promoCodeId);
        return promoCodePlanRepository.findByPromoCodeId(promoCodeId);
    }

    // ── removePlan ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void removePlan(UUID promoCodeId, UUID planId) {
        // BR-P7: verify mapping exists before deleting
        if (!promoCodePlanRepository.exists(promoCodeId, planId)) {
            log.warn("promo_plan.mapping_not_found promoCodeId={} planId={}", promoCodeId, planId);
            throw new ResourceNotFoundException(ErrorCode.PROMO_CODE_PLAN_MAPPING_NOT_FOUND,
                "No plan restriction found for promo code " + promoCodeId + " and plan " + planId);
        }

        // BR-P6: only the mapping row is deleted — promo code and plan are untouched
        promoCodePlanRepository.delete(promoCodeId, planId);
        log.info("Removed plan restriction: promoCode={} plan={}", promoCodeId, planId);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void verifyPromoCodeExists(UUID promoCodeId) {
        promoCodeRepository.findById(promoCodeId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PROMO_CODE_NOT_FOUND, "Promo code not found: " + promoCodeId));
    }

    private void verifyPlanExists(UUID planId) {
        planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId));
    }
}
