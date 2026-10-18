package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.promotion.model.ConditionEvaluationResult;
import com.company.ppmsvc.promotion.model.CustomerContext;
import com.company.ppmsvc.promotion.model.EligibilityCondition;
import com.company.ppmsvc.promotion.model.EligibilityType;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.PlanRestrictionCondition;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionApplicationReason;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.model.UsageLimitCondition;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConditionEvaluatorImpl")
class ConditionEvaluatorImplTest {

    static final UUID PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final ConditionEvaluatorImpl evaluator = new ConditionEvaluatorImpl();

    private Promotion buildPromotion(List<PromotionCondition> conditions, Integer usageCapPerUser) {
        Instant now = Instant.now();
        return Promotion.builder()
            .id(UUID.randomUUID())
            .name("Test Promo").action(new FlatDiscount(new BigDecimal("10")))
            .validFrom(LocalDate.of(2025, 1, 1)).validUntil(LocalDate.of(2025, 12, 31))
            .status(PromotionStatus.ACTIVE)
            .conditions(conditions)
            .usageCapPerUser(usageCapPerUser)
            .createdAt(now).updatedAt(now)
            .build();
    }

    @Nested
    @DisplayName("PlanRestrictionCondition")
    class PlanRestriction {

        @Test
        @DisplayName("plan not in restricted set — PLAN_NOT_ELIGIBLE")
        void restrictedToOtherPlan_fails() {
            Promotion promotion = buildPromotion(
                List.of(new PlanRestrictionCondition(Set.of(OTHER_ID))), null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 0);

            assertThat(result.pass()).isFalse();
            assertThat(result.failReason()).isEqualTo(PromotionApplicationReason.PLAN_NOT_ELIGIBLE);
        }

        @Test
        @DisplayName("empty planIds — unrestricted, PASS")
        void emptyPlanIds_passes() {
            Promotion promotion = buildPromotion(
                List.of(new PlanRestrictionCondition(Set.of())), null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 0);

            assertThat(result.pass()).isTrue();
        }
    }

    @Nested
    @DisplayName("EligibilityCondition")
    class Eligibility {

        @Test
        @DisplayName("NEW_CUSTOMER + isNewCustomer=true — PASS")
        void newCustomerRequired_isNew_passes() {
            Promotion promotion = buildPromotion(
                List.of(new EligibilityCondition(EligibilityType.NEW_CUSTOMER)), null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", true), 0);

            assertThat(result.pass()).isTrue();
        }

        @Test
        @DisplayName("NEW_CUSTOMER + isNewCustomer=false — ELIGIBILITY_VIOLATION")
        void newCustomerRequired_notNew_fails() {
            Promotion promotion = buildPromotion(
                List.of(new EligibilityCondition(EligibilityType.NEW_CUSTOMER)), null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 0);

            assertThat(result.pass()).isFalse();
            assertThat(result.failReason()).isEqualTo(PromotionApplicationReason.ELIGIBILITY_VIOLATION);
        }

        @Test
        @DisplayName("EXISTING_CUSTOMER + isNewCustomer=false — PASS")
        void existingCustomerRequired_notNew_passes() {
            Promotion promotion = buildPromotion(
                List.of(new EligibilityCondition(EligibilityType.EXISTING_CUSTOMER)), null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 0);

            assertThat(result.pass()).isTrue();
        }

        @Test
        @DisplayName("EXISTING_CUSTOMER + isNewCustomer=true — ELIGIBILITY_VIOLATION")
        void existingCustomerRequired_isNew_fails() {
            Promotion promotion = buildPromotion(
                List.of(new EligibilityCondition(EligibilityType.EXISTING_CUSTOMER)), null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", true), 0);

            assertThat(result.pass()).isFalse();
            assertThat(result.failReason()).isEqualTo(PromotionApplicationReason.ELIGIBILITY_VIOLATION);
        }
    }

    @Nested
    @DisplayName("UsageLimitCondition")
    class UsageLimit {

        @Test
        @DisplayName("currentUsageCount < cap — PASS")
        void underCap_passes() {
            Promotion promotion = buildPromotion(List.of(new UsageLimitCondition(3)), 3);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 2);

            assertThat(result.pass()).isTrue();
        }

        @Test
        @DisplayName("currentUsageCount >= cap — USAGE_LIMIT_PER_USER")
        void atCap_fails() {
            Promotion promotion = buildPromotion(List.of(new UsageLimitCondition(1)), 1);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 1);

            assertThat(result.pass()).isFalse();
            assertThat(result.failReason()).isEqualTo(PromotionApplicationReason.USAGE_LIMIT_PER_USER);
        }

        @Test
        @DisplayName("usageCapPerUser=null — unlimited, PASS regardless of usage count")
        void nullCap_alwaysPasses() {
            Promotion promotion = buildPromotion(List.of(new UsageLimitCondition(null)), null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 999);

            assertThat(result.pass()).isTrue();
        }
    }

    @Nested
    @DisplayName("Combinations")
    class Combinations {

        @Test
        @DisplayName("multiple conditions — first failure wins (short-circuit)")
        void multipleConditions_firstFailureWins() {
            Promotion promotion = buildPromotion(
                List.of(
                    new PlanRestrictionCondition(Set.of(OTHER_ID)),
                    new EligibilityCondition(EligibilityType.NEW_CUSTOMER)),
                null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 0);

            assertThat(result.pass()).isFalse();
            assertThat(result.failReason()).isEqualTo(PromotionApplicationReason.PLAN_NOT_ELIGIBLE);
        }

        @Test
        @DisplayName("empty conditions list — PASS")
        void emptyConditions_passes() {
            Promotion promotion = buildPromotion(List.of(), null);

            ConditionEvaluationResult result = evaluator.evaluate(
                promotion, PLAN_ID, new CustomerContext("cust-1", false), 0);

            assertThat(result).isEqualTo(ConditionEvaluationResult.PASS);
        }
    }
}
