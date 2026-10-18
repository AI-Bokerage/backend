package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.usecase.PricingResolver;
import com.company.ppmsvc.promotion.model.AppliedDiscount;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.EligibilityCondition;
import com.company.ppmsvc.promotion.model.EligibilityType;
import com.company.ppmsvc.promotion.model.FixedPriceDiscount;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.FreeModuleDiscount;
import com.company.ppmsvc.promotion.model.PlanRestrictionCondition;
import com.company.ppmsvc.promotion.model.PriceQuote;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionApplicationReason;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import com.company.ppmsvc.promotionredemption.port.PromotionRedemptionRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionPricingServiceImpl")
class PromotionPricingServiceImplTest {

    static final UUID PLAN_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PROMOTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID ACTOR_ID     = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock PricingResolver                    pricingResolver;
    @Mock CouponRepositoryPort                couponRepository;
    @Mock PromotionRepositoryPort             promotionRepository;
    @Mock DiscountCalculator                  discountCalculator;
    @Mock ConditionEvaluator                  conditionEvaluator;
    @Mock PromotionRedemptionRepositoryPort   redemptionRepository;

    @InjectMocks PromotionPricingServiceImpl service;

    static final CustomerContext CUSTOMER = new CustomerContext("cust-1", false);

    private PlanPrice buildPrice() {
        Instant now = Instant.now();
        return PlanPrice.builder()
            .id(UUID.randomUUID()).version(0L)
            .planId(PLAN_ID).cycle(BillingCycle.MONTHLY)
            .currency("INR").region("INDIA")
            .amount(new BigDecimal("1000.0000"))
            .taxInclusive(false)
            .effectiveFrom(LocalDate.of(2025, 1, 1))
            .active(true)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private Coupon buildCoupon(boolean active) {
        Instant now = Instant.now();
        return Coupon.builder()
            .id(UUID.randomUUID()).version(0L)
            .code("DIWALI20").promotionId(PROMOTION_ID).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private Promotion buildPromotion(PromotionStatus status, LocalDate from, LocalDate until) {
        return buildPromotion(status, from, until, List.of());
    }

    private Promotion buildPromotion(PromotionStatus status, LocalDate from, LocalDate until,
                                     List<PromotionCondition> conditions) {
        return buildPromotion(status, from, until, conditions, new FlatDiscount(new BigDecimal("200")));
    }

    private Promotion buildPromotion(PromotionStatus status, LocalDate from, LocalDate until,
                                     List<PromotionCondition> conditions, PromotionAction action) {
        Instant now = Instant.now();
        return Promotion.builder()
            .id(PROMOTION_ID).version(0L)
            .name("Diwali Sale").action(action)
            .validFrom(from).validUntil(until)
            .status(status)
            .conditions(conditions)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    @Test
    @DisplayName("no couponCode — returns NO_COUPON with base amount as final")
    void quote_noCoupon_returnsNoCoupon() {
        when(pricingResolver.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY)).thenReturn(buildPrice());

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, null);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.NO_COUPON);
        assertThat(result.finalAmount()).isEqualByComparingTo("1000.0000");
        assertThat(result.discountAmount()).isNull();
        assertThat(result.conditionsSkipped()).isTrue();
    }

    @Test
    @DisplayName("coupon not found — returns COUPON_NOT_FOUND")
    void quote_couponNotFound_returnsCouponNotFound() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(couponRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "MISSING");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.COUPON_NOT_FOUND);
    }

    @Test
    @DisplayName("inactive coupon — returns COUPON_INACTIVE")
    void quote_couponInactive_returnsCouponInactive() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(false)));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "DIWALI20");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.COUPON_INACTIVE);
    }

    @Test
    @DisplayName("promotion INACTIVE status — returns PROMOTION_INACTIVE")
    void quote_promotionInactiveStatus_returnsPromotionInactive() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(
            buildPromotion(PromotionStatus.INACTIVE, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "DIWALI20");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PROMOTION_INACTIVE);
    }

    @Test
    @DisplayName("promotion DRAFT status — also returns PROMOTION_INACTIVE")
    void quote_promotionDraftStatus_returnsPromotionInactive() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(
            buildPromotion(PromotionStatus.DRAFT, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "DIWALI20");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PROMOTION_INACTIVE);
    }

    @Test
    @DisplayName("promotion not yet started — returns PROMOTION_NOT_STARTED")
    void quote_promotionNotStarted_returnsNotStarted() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(
            buildPromotion(PromotionStatus.ACTIVE, LocalDate.now().plusDays(1), LocalDate.now().plusDays(90))));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "DIWALI20");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PROMOTION_NOT_STARTED);
    }

    @Test
    @DisplayName("promotion expired — returns PROMOTION_EXPIRED")
    void quote_promotionExpired_returnsExpired() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(
            buildPromotion(PromotionStatus.ACTIVE, LocalDate.now().minusDays(90), LocalDate.now().minusDays(1))));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "DIWALI20");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PROMOTION_EXPIRED);
    }

    @Test
    @DisplayName("valid promotion — applies discount via DiscountCalculator and returns VALID")
    void quote_valid_appliesDiscount() {
        PlanPrice price = buildPrice();
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().minusDays(10), LocalDate.now().plusDays(10));

        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(price);
        when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
        when(discountCalculator.apply(price.getAmount(), (com.company.ppmsvc.promotion.model.PriceAction) promotion.getAction())).thenReturn(
            new AppliedDiscount(price.getAmount(), new BigDecimal("200.0000"), new BigDecimal("800.0000"),
                promotion.getAction()));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "DIWALI20");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.VALID);
        assertThat(result.discountAmount()).isEqualByComparingTo("200.0000");
        assertThat(result.finalAmount()).isEqualByComparingTo("800.0000");
        assertThat(result.promotionId()).isEqualTo(PROMOTION_ID);
        assertThat(result.conditionsSkipped()).isTrue();
        assertThat(result.grantedEntitlement()).isNull();
    }

    @Test
    @DisplayName("FixedPriceDiscount promotion — discountAmount/finalAmount reflect the fixed price, no entitlement")
    void quote_fixedPriceDiscount_appliesFixedPrice() {
        PlanPrice price = buildPrice();
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().minusDays(10), LocalDate.now().plusDays(10), List.of(),
            new FixedPriceDiscount(new BigDecimal("499")));

        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(price);
        when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
        when(discountCalculator.apply(eq(price.getAmount()), any(FixedPriceDiscount.class))).thenReturn(
            new AppliedDiscount(price.getAmount(), new BigDecimal("501.0000"), new BigDecimal("499.0000"),
                promotion.getAction()));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "DIWALI20");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.VALID);
        assertThat(result.discountAmount()).isEqualByComparingTo("501.0000");
        assertThat(result.finalAmount()).isEqualByComparingTo("499.0000");
        assertThat(result.grantedEntitlement()).isNull();
    }

    @Test
    @DisplayName("FreeModuleDiscount promotion — finalAmount unchanged, grantedEntitlement populated, no discount")
    void quote_freeModuleDiscount_grantsEntitlement() {
        UUID moduleId = UUID.randomUUID();
        PlanPrice price = buildPrice();
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().minusDays(10), LocalDate.now().plusDays(10), List.of(),
            new FreeModuleDiscount(moduleId, 3));

        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(price);
        when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "DIWALI20");

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.VALID);
        assertThat(result.discountAmount()).isNull();
        assertThat(result.finalAmount()).isEqualByComparingTo(price.getAmount());
        assertThat(result.grantedEntitlement()).isNotNull();
        assertThat(result.grantedEntitlement().type())
            .isEqualTo(com.company.ppmsvc.promotion.model.EntitlementType.FREE_MODULE);
        assertThat(result.grantedEntitlement().targetId()).isEqualTo(moduleId);
        assertThat(result.grantedEntitlement().durationMonths()).isEqualTo(3);
    }

    @Nested
    @DisplayName("quoteWithCustomer()")
    class QuoteWithCustomer {

        @Test
        @DisplayName("valid customer, no conditions — VALID with discount applied, conditionsSkipped=false")
        void quoteWithCustomer_noConditions_valid() {
            PlanPrice price = buildPrice();
            Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(10));

            when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(price);
            when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
            when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(0);
            when(conditionEvaluator.evaluate(promotion, PLAN_ID, CUSTOMER, 0))
                .thenReturn(com.company.ppmsvc.promotion.model.ConditionEvaluationResult.PASS);
            when(discountCalculator.apply(price.getAmount(), (com.company.ppmsvc.promotion.model.PriceAction) promotion.getAction())).thenReturn(
                new AppliedDiscount(price.getAmount(), new BigDecimal("200.0000"), new BigDecimal("800.0000"),
                    promotion.getAction()));

            PriceQuote result = service.quoteWithCustomer(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY,
                "DIWALI20", CUSTOMER);

            assertThat(result.reason()).isEqualTo(PromotionApplicationReason.VALID);
            assertThat(result.discountAmount()).isEqualByComparingTo("200.0000");
            assertThat(result.conditionsSkipped()).isFalse();
        }

        @Test
        @DisplayName("plan restriction condition violated — PLAN_NOT_ELIGIBLE")
        void quoteWithCustomer_planRestrictionViolated_returnsPlanNotEligible() {
            PlanPrice price = buildPrice();
            UUID otherPlanId = UUID.randomUUID();
            Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(10),
                List.of(new PlanRestrictionCondition(Set.of(otherPlanId))));

            when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(price);
            when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
            when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(0);
            when(conditionEvaluator.evaluate(promotion, PLAN_ID, CUSTOMER, 0))
                .thenReturn(com.company.ppmsvc.promotion.model.ConditionEvaluationResult.fail(
                    PromotionApplicationReason.PLAN_NOT_ELIGIBLE));

            PriceQuote result = service.quoteWithCustomer(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY,
                "DIWALI20", CUSTOMER);

            assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PLAN_NOT_ELIGIBLE);
            assertThat(result.discountAmount()).isNull();
            assertThat(result.conditionsSkipped()).isFalse();
        }

        @Test
        @DisplayName("eligibility condition violated — ELIGIBILITY_VIOLATION")
        void quoteWithCustomer_eligibilityViolated_returnsEligibilityViolation() {
            PlanPrice price = buildPrice();
            Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(10),
                List.of(new EligibilityCondition(EligibilityType.NEW_CUSTOMER)));

            when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(price);
            when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
            when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(0);
            when(conditionEvaluator.evaluate(promotion, PLAN_ID, CUSTOMER, 0))
                .thenReturn(com.company.ppmsvc.promotion.model.ConditionEvaluationResult.fail(
                    PromotionApplicationReason.ELIGIBILITY_VIOLATION));

            PriceQuote result = service.quoteWithCustomer(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY,
                "DIWALI20", CUSTOMER);

            assertThat(result.reason()).isEqualTo(PromotionApplicationReason.ELIGIBILITY_VIOLATION);
        }

        @Test
        @DisplayName("usage cap per user reached — USAGE_LIMIT_PER_USER")
        void quoteWithCustomer_usageCapReached_returnsUsageLimitPerUser() {
            PlanPrice price = buildPrice();
            Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(10),
                List.of(new com.company.ppmsvc.promotion.model.UsageLimitCondition(1)));

            when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(price);
            when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon(true)));
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
            when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(1);
            when(conditionEvaluator.evaluate(promotion, PLAN_ID, CUSTOMER, 1))
                .thenReturn(com.company.ppmsvc.promotion.model.ConditionEvaluationResult.fail(
                    PromotionApplicationReason.USAGE_LIMIT_PER_USER));

            PriceQuote result = service.quoteWithCustomer(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY,
                "DIWALI20", CUSTOMER);

            assertThat(result.reason()).isEqualTo(PromotionApplicationReason.USAGE_LIMIT_PER_USER);
        }
    }

    @Nested
    @DisplayName("plan-not-found propagation")
    class PlanNotFound {

        @Test
        @DisplayName("plan/price not resolved propagates ResourceNotFoundException")
        void quote_planNotFound_propagates() {
            when(pricingResolver.resolvePrice(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

            assertThatThrownBy(() -> service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
        }
    }
}
