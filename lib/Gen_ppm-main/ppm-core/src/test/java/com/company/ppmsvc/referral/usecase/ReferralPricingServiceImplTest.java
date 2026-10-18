package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.usecase.PricingResolver;
import com.company.ppmsvc.promotion.model.AppliedDiscount;
import com.company.ppmsvc.promotion.model.ConditionEvaluationResult;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.EligibilityCondition;
import com.company.ppmsvc.promotion.model.EligibilityType;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.PlanRestrictionCondition;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReferralPricingServiceImpl")
class ReferralPricingServiceImplTest {

    static final UUID PLAN_ID      = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PROGRAM_ID   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID PROMOTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final UUID ACTOR_ID     = UUID.fromString("00000000-0000-0000-0000-000000000004");

    static final CustomerContext CUSTOMER = new CustomerContext("cust-1", true);

    @Mock PricingResolver                    pricingResolver;
    @Mock DiscountCalculator                 discountCalculator;
    @Mock ConditionEvaluator                 conditionEvaluator;
    @Mock PromotionRedemptionRepositoryPort  redemptionRepository;
    @Mock ReferralCodeRepositoryPort         referralCodeRepository;
    @Mock ReferralProgramRepositoryPort      referralProgramRepository;
    @Mock PromotionRepositoryPort            promotionRepository;

    @InjectMocks ReferralPricingServiceImpl service;

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

    private ReferralCode buildCode(ReferralCodeStatus status) {
        Instant now = Instant.now();
        return ReferralCode.builder()
            .id(UUID.randomUUID()).version(0L)
            .code("REFER-NAMAN").referralProgramId(PROGRAM_ID).referrerCustomerId("referrer-1")
            .status(status)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private ReferralProgram buildProgram(ReferralProgramStatus status) {
        Instant now = Instant.now();
        return ReferralProgram.builder()
            .id(PROGRAM_ID).version(0L)
            .name("Refer a friend")
            .referrerRewardPromotionId(UUID.randomUUID())
            .referredRewardPromotionId(PROMOTION_ID)
            .status(status)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private Promotion buildPromotion(PromotionStatus status, LocalDate from, LocalDate until,
                                     List<com.company.ppmsvc.promotion.model.PromotionCondition> conditions) {
        Instant now = Instant.now();
        return Promotion.builder()
            .id(PROMOTION_ID).version(0L)
            .name("Referred customer reward").action(new FlatDiscount(new BigDecimal("100")))
            .validFrom(from).validUntil(until)
            .status(status)
            .conditions(conditions)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private void stubHappyPathUpTo(ReferralCode code, ReferralProgram program, Promotion promotion, PlanPrice price) {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(price);
        when(referralCodeRepository.findByCode("REFER-NAMAN")).thenReturn(Optional.of(code));
        when(referralProgramRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program));
        when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(promotion));
    }

    @Test
    @DisplayName("unknown referral code — REFERRAL_CODE_NOT_FOUND")
    void quote_unknownCode_returnsCodeNotFound() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(referralCodeRepository.findByCode("MISSING")).thenReturn(Optional.empty());

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "MISSING", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.REFERRAL_CODE_NOT_FOUND);
        assertThat(result.conditionsSkipped()).isFalse();
    }

    @Test
    @DisplayName("inactive referral code — REFERRAL_CODE_INACTIVE")
    void quote_inactiveCode_returnsCodeInactive() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(referralCodeRepository.findByCode("REFER-NAMAN")).thenReturn(Optional.of(buildCode(ReferralCodeStatus.INACTIVE)));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.REFERRAL_CODE_INACTIVE);
    }

    @Test
    @DisplayName("inactive referral program — REFERRAL_PROGRAM_INACTIVE")
    void quote_inactiveProgram_returnsProgramInactive() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any())).thenReturn(buildPrice());
        when(referralCodeRepository.findByCode("REFER-NAMAN")).thenReturn(Optional.of(buildCode(ReferralCodeStatus.ACTIVE)));
        when(referralProgramRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(buildProgram(ReferralProgramStatus.INACTIVE)));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.REFERRAL_PROGRAM_INACTIVE);
    }

    @Test
    @DisplayName("reward promotion inactive — PROMOTION_INACTIVE (reused reason)")
    void quote_rewardPromotionInactive_returnsPromotionInactive() {
        ReferralCode code = buildCode(ReferralCodeStatus.ACTIVE);
        ReferralProgram program = buildProgram(ReferralProgramStatus.ACTIVE);
        Promotion promotion = buildPromotion(PromotionStatus.INACTIVE, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), List.of());
        stubHappyPathUpTo(code, program, promotion, buildPrice());

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PROMOTION_INACTIVE);
    }

    @Test
    @DisplayName("reward promotion not yet started — PROMOTION_NOT_STARTED")
    void quote_rewardPromotionNotStarted_returnsNotStarted() {
        ReferralCode code = buildCode(ReferralCodeStatus.ACTIVE);
        ReferralProgram program = buildProgram(ReferralProgramStatus.ACTIVE);
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().plusDays(1), LocalDate.now().plusDays(90), List.of());
        stubHappyPathUpTo(code, program, promotion, buildPrice());

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PROMOTION_NOT_STARTED);
    }

    @Test
    @DisplayName("reward promotion expired — PROMOTION_EXPIRED")
    void quote_rewardPromotionExpired_returnsExpired() {
        ReferralCode code = buildCode(ReferralCodeStatus.ACTIVE);
        ReferralProgram program = buildProgram(ReferralProgramStatus.ACTIVE);
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().minusDays(90), LocalDate.now().minusDays(1), List.of());
        stubHappyPathUpTo(code, program, promotion, buildPrice());

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PROMOTION_EXPIRED);
    }

    @Test
    @DisplayName("plan restriction condition violated — PLAN_NOT_ELIGIBLE")
    void quote_planRestrictionViolated_returnsPlanNotEligible() {
        ReferralCode code = buildCode(ReferralCodeStatus.ACTIVE);
        ReferralProgram program = buildProgram(ReferralProgramStatus.ACTIVE);
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().minusDays(10), LocalDate.now().plusDays(10),
            List.of(new PlanRestrictionCondition(Set.of(UUID.randomUUID()))));
        stubHappyPathUpTo(code, program, promotion, buildPrice());
        when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(0);
        when(conditionEvaluator.evaluate(promotion, PLAN_ID, CUSTOMER, 0))
            .thenReturn(ConditionEvaluationResult.fail(PromotionApplicationReason.PLAN_NOT_ELIGIBLE));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.PLAN_NOT_ELIGIBLE);
    }

    @Test
    @DisplayName("eligibility condition violated — ELIGIBILITY_VIOLATION")
    void quote_eligibilityViolated_returnsEligibilityViolation() {
        ReferralCode code = buildCode(ReferralCodeStatus.ACTIVE);
        ReferralProgram program = buildProgram(ReferralProgramStatus.ACTIVE);
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().minusDays(10), LocalDate.now().plusDays(10),
            List.of(new EligibilityCondition(EligibilityType.NEW_CUSTOMER)));
        stubHappyPathUpTo(code, program, promotion, buildPrice());
        when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(0);
        when(conditionEvaluator.evaluate(promotion, PLAN_ID, CUSTOMER, 0))
            .thenReturn(ConditionEvaluationResult.fail(PromotionApplicationReason.ELIGIBILITY_VIOLATION));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.ELIGIBILITY_VIOLATION);
    }

    @Test
    @DisplayName("usage cap per user reached — USAGE_LIMIT_PER_USER")
    void quote_usageCapReached_returnsUsageLimitPerUser() {
        ReferralCode code = buildCode(ReferralCodeStatus.ACTIVE);
        ReferralProgram program = buildProgram(ReferralProgramStatus.ACTIVE);
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().minusDays(10), LocalDate.now().plusDays(10), List.of());
        stubHappyPathUpTo(code, program, promotion, buildPrice());
        when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(1);
        when(conditionEvaluator.evaluate(promotion, PLAN_ID, CUSTOMER, 1))
            .thenReturn(ConditionEvaluationResult.fail(PromotionApplicationReason.USAGE_LIMIT_PER_USER));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.USAGE_LIMIT_PER_USER);
    }

    @Test
    @DisplayName("valid — applies discount via DiscountCalculator using the reward promotion's action")
    void quote_valid_appliesDiscountUsingRewardPromotionAction() {
        ReferralCode code = buildCode(ReferralCodeStatus.ACTIVE);
        ReferralProgram program = buildProgram(ReferralProgramStatus.ACTIVE);
        Promotion promotion = buildPromotion(PromotionStatus.ACTIVE,
            LocalDate.now().minusDays(10), LocalDate.now().plusDays(10), List.of());
        PlanPrice price = buildPrice();
        stubHappyPathUpTo(code, program, promotion, price);
        when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(0);
        when(conditionEvaluator.evaluate(promotion, PLAN_ID, CUSTOMER, 0)).thenReturn(ConditionEvaluationResult.PASS);
        com.company.ppmsvc.promotion.model.PriceAction action =
            (com.company.ppmsvc.promotion.model.PriceAction) promotion.getAction();
        when(discountCalculator.apply(eq(price.getAmount()), eq(action))).thenReturn(
            new AppliedDiscount(price.getAmount(), new BigDecimal("100.0000"), new BigDecimal("900.0000"), action));

        PriceQuote result = service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER);

        assertThat(result.reason()).isEqualTo(PromotionApplicationReason.VALID);
        assertThat(result.discountAmount()).isEqualByComparingTo("100.0000");
        assertThat(result.finalAmount()).isEqualByComparingTo("900.0000");
        assertThat(result.promotionId()).isEqualTo(PROMOTION_ID);
        assertThat(result.conditionsSkipped()).isFalse();
        verify(discountCalculator).apply(price.getAmount(), action);
    }

    @Test
    @DisplayName("plan/price not resolved propagates ResourceNotFoundException")
    void quote_planNotFound_propagates() {
        when(pricingResolver.resolvePrice(any(), any(), any(), any()))
            .thenThrow(new ResourceNotFoundException(ErrorCode.PLAN_NOT_FOUND, "Plan not found"));

        assertThatThrownBy(() -> service.quote(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, "REFER-NAMAN", CUSTOMER))
            .isInstanceOf(ResourceNotFoundException.class)
            .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PLAN_NOT_FOUND));
    }
}
