package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.planprice.usecase.PricingResolverImpl;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PricingResolverImpl")
class PricingResolverImplTest {

    static final UUID      PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID      PRICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID      ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final LocalDate TODAY    = LocalDate.now();

    @Mock PlanRepositoryPort      planRepository;
    @Mock PlanPriceRepositoryPort planPriceRepository;

    @InjectMocks PricingResolverImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private Plan buildPlan() {
        Instant now = Instant.now();
        return Plan.builder()
            .id(PLAN_ID)
            .code("PLN-0001").slug("pln-0001").name("Starter")
            .visibility(PlanVisibility.PUBLIC)
            .trialDays(0).active(true)
            .createdAt(now).updatedAt(now)
            .build();
    }

    private PlanPrice buildPrice(UUID id, LocalDate effectiveFrom,
                                  boolean active, BillingCycle cycle) {
        Instant now = Instant.now();
        return PlanPrice.builder()
            .id(id).version(0L)
            .planId(PLAN_ID)
            .cycle(cycle).currency("INR").region("INDIA")
            .amount(new BigDecimal("999.00"))
            .taxInclusive(false)
            .effectiveFrom(effectiveFrom)
            .active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    // ── PR-1 Plan validation ──────────────────────────────────────────────────

    @Nested
    @DisplayName("PR-1 — Plan validation")
    class PlanValidation {

        @Test
        @DisplayName("PR-1 — unknown plan throws PLAN_NOT_FOUND; price repository never queried")
        void resolve_unknownPlan_throwsPlanNotFound() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(planPriceRepository, never()).findByPlanIdAndRegionAndCurrency(any(), any(), any());
        }
    }

    // ── PR-2 Input normalisation ──────────────────────────────────────────────

    @Nested
    @DisplayName("PR-2 — Input normalisation")
    class InputNormalisation {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PR-2 — lowercase region is normalised to uppercase before lookup")
        void resolve_lowercaseRegion_normalisedForLookup() {
            PlanPrice price = buildPrice(PRICE_ID, TODAY.minusDays(1), true, BillingCycle.MONTHLY);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(price));

            service.resolvePrice(PLAN_ID, "india", "INR", BillingCycle.MONTHLY);

            verify(planPriceRepository).findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR");
        }

        @Test
        @DisplayName("PR-2 — padded currency is stripped and uppercased before lookup")
        void resolve_paddedCurrency_normalisedForLookup() {
            PlanPrice price = buildPrice(PRICE_ID, TODAY.minusDays(1), true, BillingCycle.MONTHLY);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(price));

            service.resolvePrice(PLAN_ID, "INDIA", "  inr  ", BillingCycle.MONTHLY);

            ArgumentCaptor<String> currCaptor = ArgumentCaptor.forClass(String.class);
            verify(planPriceRepository).findByPlanIdAndRegionAndCurrency(eq(PLAN_ID), eq("INDIA"), currCaptor.capture());
            assertThat(currCaptor.getValue()).isEqualTo("INR");
        }
    }

    // ── PR-4/5/6 Filter and select ────────────────────────────────────────────

    @Nested
    @DisplayName("PR-4/5/6 — Filter and selection")
    class FilterAndSelection {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("Single active price in the past resolves correctly")
        void resolve_singleActivePrice_resolves() {
            PlanPrice price = buildPrice(PRICE_ID, TODAY.minusDays(30), true, BillingCycle.MONTHLY);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(price));

            PlanPrice result = service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY);

            assertThat(result.getId()).isEqualTo(PRICE_ID);
        }

        @Test
        @DisplayName("PR-6 — latest effectiveFrom wins when multiple applicable rows exist")
        void resolve_multiplePrices_latestEffectiveDateWins() {
            UUID olderPriceId = UUID.fromString("00000000-0000-0000-0000-000000000010");
            UUID newerPriceId = UUID.fromString("00000000-0000-0000-0000-000000000011");

            PlanPrice older = buildPrice(olderPriceId, TODAY.minusDays(90), true, BillingCycle.MONTHLY);
            PlanPrice newer = buildPrice(newerPriceId, TODAY.minusDays(10), true, BillingCycle.MONTHLY);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(older, newer));

            PlanPrice result = service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY);

            assertThat(result.getId()).isEqualTo(newerPriceId);
        }

        @Test
        @DisplayName("PR-5 — future-dated price is ignored; earlier applicable price wins")
        void resolve_futurePriceIgnored_earlierPriceSelected() {
            UUID pastPriceId   = UUID.fromString("00000000-0000-0000-0000-000000000010");
            UUID futurePriceId = UUID.fromString("00000000-0000-0000-0000-000000000011");

            PlanPrice past   = buildPrice(pastPriceId,   TODAY.minusDays(30), true, BillingCycle.MONTHLY);
            PlanPrice future = buildPrice(futurePriceId, TODAY.plusDays(30),  true, BillingCycle.MONTHLY);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(past, future));

            PlanPrice result = service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY);

            assertThat(result.getId()).isEqualTo(pastPriceId);
        }

        @Test
        @DisplayName("PR-4 — inactive price is excluded from resolution")
        void resolve_inactivePrice_ignored() {
            PlanPrice active   = buildPrice(PRICE_ID,                                         TODAY.minusDays(30), true,  BillingCycle.MONTHLY);
            PlanPrice inactive = buildPrice(UUID.fromString("00000000-0000-0000-0000-000000000099"), TODAY.minusDays(10), false, BillingCycle.MONTHLY);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(active, inactive));

            PlanPrice result = service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY);

            assertThat(result.getId()).isEqualTo(PRICE_ID);
        }

        @Test
        @DisplayName("PR-4 — cycle mismatch is excluded from resolution")
        void resolve_cycleMismatch_ignored() {
            PlanPrice monthly = buildPrice(PRICE_ID, TODAY.minusDays(10), true, BillingCycle.MONTHLY);
            PlanPrice annual  = buildPrice(UUID.fromString("00000000-0000-0000-0000-000000000099"),
                TODAY.minusDays(10), true, BillingCycle.ANNUAL);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(monthly, annual));

            // Request MONTHLY — ANNUAL row must be ignored
            PlanPrice result = service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY);

            assertThat(result.getId()).isEqualTo(PRICE_ID);
        }
    }

    // ── PR-7 No applicable price ──────────────────────────────────────────────

    @Nested
    @DisplayName("PR-7 — No applicable price")
    class NoApplicablePrice {

        @BeforeEach
        void planExists() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(buildPlan()));
        }

        @Test
        @DisplayName("PR-7 — empty candidate list throws PLAN_PRICE_NOT_RESOLVED")
        void resolve_noCandidates_throwsPriceNotResolved() {
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of());

            assertThatThrownBy(() ->
                service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("PR-7 — all-future candidates throw PLAN_PRICE_NOT_RESOLVED")
        void resolve_allFuturePrices_throwsPriceNotResolved() {
            PlanPrice future = buildPrice(PRICE_ID, TODAY.plusDays(30), true, BillingCycle.MONTHLY);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(future));

            assertThatThrownBy(() ->
                service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY))
                .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("PR-7 — all-inactive candidates throw PLAN_PRICE_NOT_RESOLVED")
        void resolve_allInactivePrices_throwsPriceNotResolved() {
            PlanPrice inactive = buildPrice(PRICE_ID, TODAY.minusDays(10), false, BillingCycle.MONTHLY);
            when(planPriceRepository.findByPlanIdAndRegionAndCurrency(PLAN_ID, "INDIA", "INR"))
                .thenReturn(List.of(inactive));

            assertThatThrownBy(() ->
                service.resolvePrice(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY))
                .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
