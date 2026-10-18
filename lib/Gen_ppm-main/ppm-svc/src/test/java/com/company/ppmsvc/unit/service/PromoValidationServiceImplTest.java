package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.promocode.model.PromoValidationReason;
import com.company.ppmsvc.promocode.model.PromoValidationResult;
import com.company.ppmsvc.promocode.usecase.PromoValidationServiceImpl;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocodeplan.model.PromoCodePlan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromoValidationServiceImpl")
class PromoValidationServiceImplTest {

    static final UUID      PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID      CODE_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID      OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final LocalDate TODAY    = LocalDate.now();

    @Mock PromoCodeRepositoryPort     promoCodeRepository;
    @Mock PromoCodePlanRepositoryPort promoCodePlanRepository;
    @Mock PlanRepositoryPort          planRepository;

    @InjectMocks PromoValidationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Plan buildPlan() {
        Instant now = Instant.now();
        return Plan.builder()
            .id(PLAN_ID)
            .code("PLN-0001").slug("pln-0001").name("Starter")
            .visibility(com.company.ppmsvc.plan.model.PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build();
    }

    private PromoCode buildPromoCode(LocalDate from, LocalDate until,
                                     boolean active, Integer usageCap,
                                     int usageCount, boolean firstTimeOnly) {
        Instant now = Instant.now();
        return PromoCode.builder()
            .id(CODE_ID).version(0L)
            .code("SUMMER20")
            .discountType(DiscountType.PERCENTAGE).value(new BigDecimal("20.00"))
            .validFrom(from).validUntil(until)
            .usageCap(usageCap).usageCount(usageCount)
            .firstTimeOnly(firstTimeOnly).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(PLAN_ID).updatedBy(PLAN_ID)
            .build();
    }

    private PromoCode validPromo() {
        return buildPromoCode(TODAY.minusDays(30), TODAY.plusDays(30),
            true, null, 0, false);
    }

    private PromoCodePlan restriction(UUID planId) {
        return PromoCodePlan.builder()
            .id(UUID.randomUUID()).promoCodeId(CODE_ID).planId(planId)
            .createdAt(Instant.now()).createdBy(PLAN_ID)
            .build();
    }

    // ── plan validation ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("Plan validation")
    class PlanValidation {

        @Test
        @DisplayName("PV-0 — unknown plan throws ResourceNotFoundException PLAN_NOT_FOUND")
        void validate_unknownPlan_throwsPlanNotFound() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.validate("SUMMER20", PLAN_ID))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(promoCodeRepository, never()).findByCode(any());
        }
    }

    // ── promo existence & state rules ─────────────────────────────────────────

    @Nested
    @DisplayName("PV-1 — Promo existence")
    class PromoExistence {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PV-1 — missing code returns PROMO_NOT_FOUND")
        void validate_missingPromoCode_returnsNotFound() {
            when(promoCodeRepository.findByCode("MISSING")).thenReturn(Optional.empty());

            PromoValidationResult result =
                service.validate("MISSING", PLAN_ID);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).isEqualTo(PromoValidationReason.PROMO_NOT_FOUND);
            assertThat(result.code()).isEqualTo("MISSING");
        }
    }

    @Nested
    @DisplayName("PV-2 — Active flag")
    class ActiveFlag {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PV-2 — inactive promo returns PROMO_INACTIVE")
        void validate_inactivePromo_returnsInactive() {
            PromoCode inactive = buildPromoCode(TODAY.minusDays(30), TODAY.plusDays(30),
                false, null, 0, false);
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(inactive));

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).isEqualTo(PromoValidationReason.PROMO_INACTIVE);
        }
    }

    @Nested
    @DisplayName("PV-3 — Validity window start")
    class ValidityStart {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PV-3 — future promo returns PROMO_NOT_STARTED")
        void validate_futurePromo_returnsNotStarted() {
            PromoCode future = buildPromoCode(TODAY.plusDays(1), TODAY.plusDays(90),
                true, null, 0, false);
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(future));

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).isEqualTo(PromoValidationReason.PROMO_NOT_STARTED);
        }
    }

    @Nested
    @DisplayName("PV-4 — Validity window end")
    class ValidityEnd {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PV-4 — expired promo returns PROMO_EXPIRED")
        void validate_expiredPromo_returnsExpired() {
            PromoCode expired = buildPromoCode(TODAY.minusDays(90), TODAY.minusDays(1),
                true, null, 0, false);
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(expired));

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).isEqualTo(PromoValidationReason.PROMO_EXPIRED);
        }
    }

    @Nested
    @DisplayName("PV-5 — Usage cap")
    class UsageCap {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PV-5 — usage count at cap returns USAGE_CAP_REACHED")
        void validate_usageCapReached_returnsCapReached() {
            PromoCode capped = buildPromoCode(TODAY.minusDays(30), TODAY.plusDays(30),
                true, 100, 100, false);
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(capped));

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).isEqualTo(PromoValidationReason.USAGE_CAP_REACHED);
        }

        @Test
        @DisplayName("PV-5 — null usage cap is treated as unlimited — proceeds to valid")
        void validate_nullUsageCap_treatedAsUnlimited() {
            PromoCode unlimited = buildPromoCode(TODAY.minusDays(30), TODAY.plusDays(30),
                true, null, 9999, false);
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(unlimited));
            when(promoCodePlanRepository.findByPromoCodeId(CODE_ID)).thenReturn(List.of());

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isTrue();
        }
    }

    @Nested
    @DisplayName("PV-6/7 — Plan restrictions")
    class PlanRestrictions {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PV-6 — restricted promo with eligible plan returns VALID")
        void validate_restrictedPromo_eligiblePlan_returnsValid() {
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(validPromo()));
            when(promoCodePlanRepository.findByPromoCodeId(CODE_ID))
                .thenReturn(List.of(restriction(PLAN_ID)));

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isTrue();
            assertThat(result.reason()).isEqualTo(PromoValidationReason.VALID);
        }

        @Test
        @DisplayName("PV-6 — restricted promo with ineligible plan returns PLAN_NOT_ELIGIBLE")
        void validate_restrictedPromo_ineligiblePlan_returnsNotEligible() {
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(validPromo()));
            // restriction points to OTHER_ID, but request is for PLAN_ID
            when(promoCodePlanRepository.findByPromoCodeId(CODE_ID))
                .thenReturn(List.of(restriction(OTHER_ID)));

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isFalse();
            assertThat(result.reason()).isEqualTo(PromoValidationReason.PLAN_NOT_ELIGIBLE);
        }

        @Test
        @DisplayName("PV-7 — unrestricted promo (empty list) is valid for any plan")
        void validate_unrestrictedPromo_validForAnyPlan() {
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(validPromo()));
            when(promoCodePlanRepository.findByPromoCodeId(CODE_ID)).thenReturn(List.of());

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isTrue();
        }
    }

    @Nested
    @DisplayName("PV-8 — firstTimeOnly passthrough")
    class FirstTimeOnly {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PV-8 — firstTimeOnly=true is surfaced in response and does not reject")
        void validate_firstTimeOnly_isPassedThroughWithoutRejection() {
            PromoCode ftOnly = buildPromoCode(TODAY.minusDays(30), TODAY.plusDays(30),
                true, null, 0, true);
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(ftOnly));
            when(promoCodePlanRepository.findByPromoCodeId(CODE_ID)).thenReturn(List.of());

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isTrue();
            assertThat(result.firstTimeOnly()).isTrue();
        }
    }

    @Nested
    @DisplayName("PV-9 — Valid response fields")
    class ValidResponseFields {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PV-9 — valid response carries full discount metadata")
        void validate_valid_returnsFullDiscountMetadata() {
            PromoCode promo = validPromo();
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(promo));
            when(promoCodePlanRepository.findByPromoCodeId(CODE_ID)).thenReturn(List.of());

            PromoValidationResult result =
                service.validate("SUMMER20", PLAN_ID);

            assertThat(result.valid()).isTrue();
            assertThat(result.code()).isEqualTo("SUMMER20");
            assertThat(result.promoCodeId()).isEqualTo(CODE_ID);
            assertThat(result.discountType()).isEqualTo(DiscountType.PERCENTAGE);
            assertThat(result.discountValue()).isEqualByComparingTo("20.00");
            assertThat(result.reason()).isEqualTo(PromoValidationReason.VALID);
            assertThat(result.validUntil()).isEqualTo(promo.getValidUntil());
        }
    }
}
