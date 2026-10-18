package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.usecase.PricingResolver;
import com.company.ppmsvc.promotion.model.AppliedDiscount;
import com.company.ppmsvc.promotion.model.AppliedEntitlement;
import com.company.ppmsvc.promotion.model.ConditionEvaluationResult;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.EntitlementAction;
import com.company.ppmsvc.promotion.model.PriceAction;
import com.company.ppmsvc.promotion.model.PriceQuote;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionApplicationReason;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import com.company.ppmsvc.promotion.usecase.ConditionEvaluator;
import com.company.ppmsvc.promotion.usecase.DiscountCalculator;
import com.company.ppmsvc.promotionredemption.port.PromotionRedemptionRepositoryPort;
import com.company.ppmsvc.referral.model.ReferralCode;
import com.company.ppmsvc.referral.model.ReferralProgram;
import com.company.ppmsvc.referral.port.ReferralCodeRepositoryPort;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the referral pipeline documented on {@link ReferralPricingService}.
 * Pure read. Reuses {@link PricingResolver}, {@link DiscountCalculator}, and
 * {@link ConditionEvaluator} as-is; no shared base class with {@code
 * PromotionPricingServiceImpl} — only the engines are reused, orchestration
 * glue is duplicated but kept readable via small private helpers below.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralPricingServiceImpl implements ReferralPricingService {

    private final PricingResolver                 pricingResolver;
    private final DiscountCalculator               discountCalculator;
    private final ConditionEvaluator               conditionEvaluator;
    private final PromotionRedemptionRepositoryPort redemptionRepository;
    private final ReferralCodeRepositoryPort        referralCodeRepository;
    private final ReferralProgramRepositoryPort     referralProgramRepository;
    private final PromotionRepositoryPort           promotionRepository;

    @Override
    @Transactional(readOnly = true)
    public PriceQuote quote(UUID planId, String region, String currency, BillingCycle cycle, String referralCode,
                            CustomerContext customer) {
        // Stage 1
        PlanPrice base = pricingResolver.resolvePrice(planId, region, currency, cycle);

        // Stages 2-3
        Resolved resolved = resolveRewardPromotion(referralCode);
        if (resolved.promotion() == null) {
            return noDiscount(base, resolved.failReason());
        }
        Promotion promotion = resolved.promotion();

        // Stage 4
        Optional<PromotionApplicationReason> windowFailure = evaluatePromotionWindow(promotion);
        if (windowFailure.isPresent()) {
            return noDiscount(base, windowFailure.get());
        }

        // Stage 4.5
        Optional<PromotionApplicationReason> conditionFailure = evaluateConditions(promotion, planId, customer);
        if (conditionFailure.isPresent()) {
            return noDiscount(base, conditionFailure.get());
        }

        // Stages 5-6
        return applyAndQuote(base, promotion, currency);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private record Resolved(Promotion promotion, PromotionApplicationReason failReason) {
        static Resolved ok(Promotion promotion) {
            return new Resolved(promotion, null);
        }
        static Resolved fail(PromotionApplicationReason reason) {
            return new Resolved(null, reason);
        }
    }

    /** Stages 2-3: referral code -&gt; program -&gt; reward promotion. */
    private Resolved resolveRewardPromotion(String referralCode) {
        Optional<ReferralCode> optCode = referralCodeRepository.findByCode(referralCode);
        if (optCode.isEmpty()) {
            log.debug("Referral quote — code not found: code={}", referralCode);
            return Resolved.fail(PromotionApplicationReason.REFERRAL_CODE_NOT_FOUND);
        }
        ReferralCode code = optCode.get();

        if (code.getStatus() != ReferralCodeStatus.ACTIVE) {
            return Resolved.fail(PromotionApplicationReason.REFERRAL_CODE_INACTIVE);
        }

        Optional<ReferralProgram> optProgram = referralProgramRepository.findById(code.getReferralProgramId());
        if (optProgram.isEmpty() || optProgram.get().getStatus() != ReferralProgramStatus.ACTIVE) {
            return Resolved.fail(PromotionApplicationReason.REFERRAL_PROGRAM_INACTIVE);
        }
        ReferralProgram program = optProgram.get();

        Optional<Promotion> optPromotion = promotionRepository.findById(program.getReferredRewardPromotionId());
        if (optPromotion.isEmpty()) {
            log.warn("Referral program {} references a missing reward promotion {}",
                program.getId(), program.getReferredRewardPromotionId());
            return Resolved.fail(PromotionApplicationReason.REFERRAL_PROGRAM_INACTIVE);
        }
        return Resolved.ok(optPromotion.get());
    }

    /** Stage 4: status + validity window — identical logic to the coupon pipeline. */
    private Optional<PromotionApplicationReason> evaluatePromotionWindow(Promotion promotion) {
        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            return Optional.of(PromotionApplicationReason.PROMOTION_INACTIVE);
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(promotion.getValidFrom())) {
            return Optional.of(PromotionApplicationReason.PROMOTION_NOT_STARTED);
        }
        if (today.isAfter(promotion.getValidUntil())) {
            return Optional.of(PromotionApplicationReason.PROMOTION_EXPIRED);
        }
        return Optional.empty();
    }

    /** Stage 4.5: conditions, including the per-user usage cap read from the redemption ledger. */
    private Optional<PromotionApplicationReason> evaluateConditions(Promotion promotion, UUID planId,
                                                                     CustomerContext customer) {
        int currentUsageCount = redemptionRepository.countByPromotionIdAndCustomerId(
            promotion.getId(), customer.customerId());
        ConditionEvaluationResult result = conditionEvaluator.evaluate(promotion, planId, customer, currentUsageCount);
        return result.pass() ? Optional.empty() : Optional.of(result.failReason());
    }

    /** Stages 5-6: apply the action (price reduction or entitlement grant) and produce the quote. */
    private PriceQuote applyAndQuote(PlanPrice base, Promotion promotion, String currency) {
        BigDecimal discountAmount = null;
        BigDecimal finalAmount = base.getAmount();
        AppliedEntitlement grantedEntitlement = null;

        switch (promotion.getAction()) {
            case PriceAction pa -> {
                AppliedDiscount discount = discountCalculator.apply(base.getAmount(), pa);
                discountAmount = discount.discountAmount();
                finalAmount = discount.finalAmount();
            }
            case EntitlementAction ea -> grantedEntitlement = AppliedEntitlement.from(ea);
        }

        log.debug("Referral quote — applied promotionId={} discount={} entitlement={}",
            promotion.getId(), discountAmount, grantedEntitlement);
        return new PriceQuote(
            base.getAmount(), currency, discountAmount, finalAmount,
            PromotionApplicationReason.VALID, promotion.getId(), promotion.getAction(), false, grantedEntitlement);
    }

    private static PriceQuote noDiscount(PlanPrice base, PromotionApplicationReason reason) {
        return new PriceQuote(base.getAmount(), base.getCurrency(), null, base.getAmount(), reason, null, null,
            false, null);
    }
}
