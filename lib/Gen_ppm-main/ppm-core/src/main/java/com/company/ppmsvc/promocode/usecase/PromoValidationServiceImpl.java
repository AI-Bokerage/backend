package com.company.ppmsvc.promocode.usecase;

import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocode.model.PromoValidationReason;
import com.company.ppmsvc.promocode.model.PromoValidationResult;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pure read implementation of the Promo Validation Engine (PPM-08).
 *
 * <p>Validation rules applied in order:
 * <ol>
 *   <li>Plan must exist (throws 404 — only exception from this engine)</li>
 *   <li>PV-1  Promo must exist</li>
 *   <li>PV-2  Promo must be active</li>
 *   <li>PV-3  Current date must be &gt;= validFrom</li>
 *   <li>PV-4  Current date must be &lt;= validUntil</li>
 *   <li>PV-5  Usage cap must not be exhausted</li>
 *   <li>PV-6/7 If plan restrictions exist, planId must be in the list</li>
 *   <li>PV-8  firstTimeOnly is informational — never a rejection reason</li>
 *   <li>PV-9  All rules pass → valid=true with full discount metadata</li>
 * </ol>
 *
 * <p>This service never writes to the database. Usage count increment belongs
 * to the future BSM checkout integration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromoValidationServiceImpl implements PromoValidationService {

    private final PromoCodeRepositoryPort     promoCodeRepository;
    private final PromoCodePlanRepositoryPort promoCodePlanRepository;
    private final PlanRepositoryPort          planRepository;

    @Override
    @Transactional(readOnly = true)
    public PromoValidationResult validate(String code, UUID planId) {
        // Plan must exist before running promo checks (only hard failure in this engine)
        planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PLAN_NOT_FOUND, "Plan not found: " + planId));

        // PV-1: promo must exist (case-sensitive; callers must pass normalised code)
        Optional<PromoCode> optPromo = promoCodeRepository.findByCode(code);
        if (optPromo.isEmpty()) {
            log.debug("Validation failed — promo not found: code={}", code);
            return new PromoValidationResult(
                false, code, null, null, null,
                PromoValidationReason.PROMO_NOT_FOUND, null, false);
        }
        PromoCode promo = optPromo.get();

        // PV-2: active flag
        if (!Boolean.TRUE.equals(promo.getActive())) {
            return invalid(promo, PromoValidationReason.PROMO_INACTIVE);
        }

        LocalDate today = LocalDate.now();

        // PV-3: not yet in validity window
        if (today.isBefore(promo.getValidFrom())) {
            return invalid(promo, PromoValidationReason.PROMO_NOT_STARTED);
        }

        // PV-4: past validity window
        if (today.isAfter(promo.getValidUntil())) {
            return invalid(promo, PromoValidationReason.PROMO_EXPIRED);
        }

        // PV-5: usage cap exhausted
        if (promo.getUsageCap() != null && promo.getUsageCount() >= promo.getUsageCap()) {
            return invalid(promo, PromoValidationReason.USAGE_CAP_REACHED);
        }

        // PV-6/PV-7: plan restrictions (empty list → unrestricted → applies to all plans)
        List<PromoCodePlan> restrictions = promoCodePlanRepository.findByPromoCodeId(promo.getId());
        if (!restrictions.isEmpty()) {
            boolean eligible = restrictions.stream()
                .anyMatch(r -> r.getPlanId().equals(planId));
            if (!eligible) {
                return invalid(promo, PromoValidationReason.PLAN_NOT_ELIGIBLE);
            }
        }

        // PV-8/PV-9: all rules passed — return full discount metadata
        log.debug("Validation passed: code={} planId={}", promo.getCode(), planId);
        return new PromoValidationResult(
            true,
            promo.getCode(),
            promo.getId(),
            promo.getDiscountType(),
            promo.getValue(),
            PromoValidationReason.VALID,
            promo.getValidUntil(),
            Boolean.TRUE.equals(promo.getFirstTimeOnly()));
    }

    private PromoValidationResult invalid(PromoCode promo, PromoValidationReason reason) {
        log.debug("Validation failed — {}: code={}", reason.getValue(), promo.getCode());
        return new PromoValidationResult(
            false, promo.getCode(), promo.getId(), null, null, reason, null, false);
    }
}
