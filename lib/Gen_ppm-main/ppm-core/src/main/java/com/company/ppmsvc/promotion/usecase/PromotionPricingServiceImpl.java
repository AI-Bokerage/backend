package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
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
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import com.company.ppmsvc.promotionredemption.port.PromotionRedemptionRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the Promotion Processing Pipeline documented on {@link
 * PromotionPricingService}. Pure read — never mutates coupon/promotion state
 * (recording a redemption is the caller's responsibility via {@code
 * PromotionRedemptionService}, not this pricing engine).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionPricingServiceImpl implements PromotionPricingService {

    private final PricingResolver                    pricingResolver;
    private final CouponRepositoryPort                couponRepository;
    private final PromotionRepositoryPort             promotionRepository;
    private final DiscountCalculator                  discountCalculator;
    private final ConditionEvaluator                   conditionEvaluator;
    private final PromotionRedemptionRepositoryPort    redemptionRepository;

    @Override
    @Transactional(readOnly = true)
    public PriceQuote quote(UUID planId, String region, String currency, BillingCycle cycle, String couponCode) {
        return resolve(planId, region, currency, cycle, couponCode, null, true);
    }

    @Override
    @Transactional(readOnly = true)
    public PriceQuote quoteWithCustomer(UUID planId, String region, String currency, BillingCycle cycle,
                                        String couponCode, CustomerContext customer) {
        return resolve(planId, region, currency, cycle, couponCode, customer, false);
    }

    private PriceQuote resolve(UUID planId, String region, String currency, BillingCycle cycle, String couponCode,
                               CustomerContext customer, boolean conditionsSkipped) {
        // Stage 1: resolve base price (propagates PLAN_NOT_FOUND / PLAN_PRICE_NOT_RESOLVED as 404)
        PlanPrice base = pricingResolver.resolvePrice(planId, region, currency, cycle);

        if (couponCode == null) {
            return noDiscount(base, PromotionApplicationReason.NO_COUPON, conditionsSkipped);
        }

        // Stage 2: resolve coupon
        Optional<Coupon> optCoupon = couponRepository.findByCode(couponCode);
        if (optCoupon.isEmpty()) {
            log.debug("Quote — coupon not found: code={}", couponCode);
            return noDiscount(base, PromotionApplicationReason.COUPON_NOT_FOUND, conditionsSkipped);
        }
        Coupon coupon = optCoupon.get();

        if (!Boolean.TRUE.equals(coupon.getActive())) {
            return noDiscount(base, PromotionApplicationReason.COUPON_INACTIVE, conditionsSkipped);
        }

        // Stage 3: resolve promotion
        Optional<Promotion> optPromotion = promotionRepository.findById(coupon.getPromotionId());
        if (optPromotion.isEmpty()) {
            return noDiscount(base, PromotionApplicationReason.PROMOTION_INACTIVE, conditionsSkipped);
        }
        Promotion promotion = optPromotion.get();

        // Stage 4: evaluate status + validity window
        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            return noDiscount(base, PromotionApplicationReason.PROMOTION_INACTIVE, conditionsSkipped);
        }
        LocalDate today = LocalDate.now();
        if (today.isBefore(promotion.getValidFrom())) {
            return noDiscount(base, PromotionApplicationReason.PROMOTION_NOT_STARTED, conditionsSkipped);
        }
        if (today.isAfter(promotion.getValidUntil())) {
            return noDiscount(base, PromotionApplicationReason.PROMOTION_EXPIRED, conditionsSkipped);
        }

        // Stage 4.5: evaluate conditions — only when a CustomerContext is available
        if (!conditionsSkipped) {
            int currentUsageCount = redemptionRepository.countByPromotionIdAndCustomerId(
                promotion.getId(), customer.customerId());
            ConditionEvaluationResult conditionResult =
                conditionEvaluator.evaluate(promotion, planId, customer, currentUsageCount);
            if (!conditionResult.pass()) {
                return noDiscount(base, conditionResult.failReason(), false);
            }
        }

        // Stages 5-6: apply action (price reduction or entitlement grant) and produce the quote
        return applyAndQuote(base, promotion, currency, conditionsSkipped);
    }

    private PriceQuote applyAndQuote(PlanPrice base, Promotion promotion, String currency, boolean conditionsSkipped) {
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

        log.debug("Quote — applied promotionId={} discount={} entitlement={}",
            promotion.getId(), discountAmount, grantedEntitlement);
        return new PriceQuote(
            base.getAmount(), currency, discountAmount, finalAmount,
            PromotionApplicationReason.VALID, promotion.getId(), promotion.getAction(), conditionsSkipped,
            grantedEntitlement);
    }

    private static PriceQuote noDiscount(PlanPrice base, PromotionApplicationReason reason, boolean conditionsSkipped) {
        return new PriceQuote(base.getAmount(), base.getCurrency(), null, base.getAmount(), reason, null, null,
            conditionsSkipped, null);
    }
}
