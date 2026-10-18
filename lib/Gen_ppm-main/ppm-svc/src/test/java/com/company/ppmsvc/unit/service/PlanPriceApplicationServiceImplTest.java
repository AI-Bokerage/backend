package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.planprice.model.PlanPrice;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.planprice.usecase.PlanPriceApplicationServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlanPriceApplicationServiceImpl")
class PlanPriceApplicationServiceImplTest {

    static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID PRICE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    static final LocalDate EFFECTIVE = LocalDate.of(2025, 1, 1);

    @Mock PlanPriceRepositoryPort planPriceRepository;
    @Mock PlanRepositoryPort      planRepository;

    @InjectMocks PlanPriceApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private PlanPrice buildPrice(UUID id, String region, String currency,
                                  BillingCycle cycle, BigDecimal amount, boolean active) {
        Instant now = Instant.now();
        return PlanPrice.builder()
            .id(id).version(0L)
            .planId(PLAN_ID)
            .region(region).currency(currency)
            .cycle(cycle).amount(amount)
            .taxInclusive(false).effectiveFrom(EFFECTIVE).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private PlanPrice buildPrice() {
        return buildPrice(PRICE_ID, "INDIA", "INR", BillingCycle.MONTHLY, new BigDecimal("499"), true);
    }

    private void stubPlanExists() {
        when(planRepository.findById(PLAN_ID)).thenReturn(Optional.of(Mockito.mock(Plan.class)));
    }

    // ── createPrice ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPrice()")
    class CreatePrice {

        @Test
        @DisplayName("success — saves price and returns it")
        void create_success() {
            stubPlanExists();
            when(planPriceRepository.exists(any(), any(), any(), any(), any())).thenReturn(false);
            PlanPrice saved = buildPrice();
            when(planPriceRepository.save(any())).thenReturn(saved);

            PlanPrice result = service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                "INR", "INDIA", new BigDecimal("499"), null, EFFECTIVE);

            assertThat(result.getId()).isEqualTo(PRICE_ID);
            verify(planPriceRepository).save(any());
        }

        @Test
        @DisplayName("BR-1 — plan not found throws PLAN_NOT_FOUND")
        void create_planNotFound_throws() {
            when(planRepository.findById(PLAN_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                    "INR", "INDIA", new BigDecimal("499"), null, EFFECTIVE))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_NOT_FOUND));

            verify(planPriceRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-7 — duplicate active pricing key throws PLAN_PRICE_ALREADY_EXISTS")
        void create_duplicateKey_throws() {
            stubPlanExists();
            when(planPriceRepository.exists(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, EFFECTIVE))
                .thenReturn(true);

            assertThatThrownBy(() -> service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                    "INR", "INDIA", new BigDecimal("499"), null, EFFECTIVE))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_PRICE_ALREADY_EXISTS));

            verify(planPriceRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-2 — amount zero throws VALIDATION_ERROR")
        void create_amountZero_throws() {
            stubPlanExists();

            assertThatThrownBy(() -> service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                    "INR", "INDIA", BigDecimal.ZERO, null, EFFECTIVE))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(planPriceRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-2 — negative amount throws VALIDATION_ERROR")
        void create_amountNegative_throws() {
            stubPlanExists();

            assertThatThrownBy(() -> service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                    "INR", "INDIA", new BigDecimal("-1"), null, EFFECTIVE))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(planPriceRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-3 — currency normalised to uppercase regardless of input case")
        void create_currencyNormalised() {
            stubPlanExists();
            when(planPriceRepository.exists(PLAN_ID, "INDIA", "USD", BillingCycle.MONTHLY, EFFECTIVE))
                .thenReturn(false);
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                "usd", "INDIA", new BigDecimal("9.99"), null, EFFECTIVE);

            verify(planPriceRepository).save(captor.capture());
            assertThat(captor.getValue().getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("BR-3 — currency with mixed case and whitespace normalised to uppercase")
        void create_currencyMixedCaseAndWhitespace_normalised() {
            stubPlanExists();
            when(planPriceRepository.exists(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, EFFECTIVE))
                .thenReturn(false);
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                "  inr  ", "INDIA", new BigDecimal("499"), null, EFFECTIVE);

            verify(planPriceRepository).save(captor.capture());
            assertThat(captor.getValue().getCurrency()).isEqualTo("INR");
        }

        @Test
        @DisplayName("BR-4 — region normalised to uppercase regardless of input case")
        void create_regionNormalised() {
            stubPlanExists();
            when(planPriceRepository.exists(PLAN_ID, "US", "USD", BillingCycle.MONTHLY, EFFECTIVE))
                .thenReturn(false);
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                "USD", "us", new BigDecimal("9.99"), null, EFFECTIVE);

            verify(planPriceRepository).save(captor.capture());
            assertThat(captor.getValue().getRegion()).isEqualTo("US");
        }

        @Test
        @DisplayName("BR-4 — region with mixed case and whitespace normalised to uppercase")
        void create_regionMixedCaseAndWhitespace_normalised() {
            stubPlanExists();
            when(planPriceRepository.exists(PLAN_ID, "EU", "EUR", BillingCycle.ANNUAL, EFFECTIVE))
                .thenReturn(false);
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.ANNUAL,
                "EUR", "  eu  ", new BigDecimal("99.99"), null, EFFECTIVE);

            verify(planPriceRepository).save(captor.capture());
            assertThat(captor.getValue().getRegion()).isEqualTo("EU");
        }

        @Test
        @DisplayName("BR-5 — taxInclusive defaults to false when null")
        void create_taxInclusiveNull_defaultsFalse() {
            stubPlanExists();
            when(planPriceRepository.exists(any(), any(), any(), any(), any())).thenReturn(false);
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                "INR", "INDIA", new BigDecimal("499"), null, EFFECTIVE);

            verify(planPriceRepository).save(captor.capture());
            assertThat(captor.getValue().isTaxInclusive()).isFalse();
        }

        @Test
        @DisplayName("active is always true on create regardless of request content")
        void create_activeAlwaysTrue() {
            stubPlanExists();
            when(planPriceRepository.exists(any(), any(), any(), any(), any())).thenReturn(false);
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                "INR", "INDIA", new BigDecimal("499"), null, EFFECTIVE);

            verify(planPriceRepository).save(captor.capture());
            assertThat(captor.getValue().isActive()).isTrue();
        }

        @Test
        @DisplayName("audit fields — createdBy and updatedBy set from the given actorId")
        void create_auditFieldsSetFromActor() {
            stubPlanExists();
            when(planPriceRepository.exists(any(), any(), any(), any(), any())).thenReturn(false);
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                "INR", "INDIA", new BigDecimal("499"), null, EFFECTIVE);

            verify(planPriceRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("future effectiveFrom is accepted — no restriction to today or earlier")
        void create_futureEffectiveFrom_accepted() {
            stubPlanExists();
            LocalDate future = LocalDate.now().plusYears(1);
            when(planPriceRepository.exists(PLAN_ID, "INDIA", "INR", BillingCycle.MONTHLY, future))
                .thenReturn(false);
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.createPrice(ACTOR_ID, PLAN_ID, BillingCycle.MONTHLY,
                "INR", "INDIA", new BigDecimal("599"), null, future);

            verify(planPriceRepository).save(captor.capture());
            assertThat(captor.getValue().getEffectiveFrom()).isEqualTo(future);
        }
    }

    // ── updatePrice ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePrice()")
    class UpdatePrice {

        @Test
        @DisplayName("success — updates mutable fields and returns them")
        void update_success() {
            PlanPrice existing = buildPrice();
            PlanPrice saved    = buildPrice(PRICE_ID, "INDIA", "INR", BillingCycle.MONTHLY,
                new BigDecimal("599"), true);

            when(planPriceRepository.findById(PRICE_ID)).thenReturn(Optional.of(existing));
            when(planPriceRepository.save(any())).thenReturn(saved);

            PlanPrice result = service.updatePrice(ACTOR_ID, PRICE_ID, new BigDecimal("599"), null, null);

            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("599"));
            verify(planPriceRepository).save(any());
        }

        @Test
        @DisplayName("not found — throws PLAN_PRICE_NOT_FOUND")
        void update_notFound_throws() {
            when(planPriceRepository.findById(PRICE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePrice(ACTOR_ID, PRICE_ID, new BigDecimal("599"), null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_PRICE_NOT_FOUND));

            verify(planPriceRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-2 — updating to zero amount throws VALIDATION_ERROR")
        void update_amountZero_throws() {
            when(planPriceRepository.findById(PRICE_ID)).thenReturn(Optional.of(buildPrice()));

            assertThatThrownBy(() -> service.updatePrice(ACTOR_ID, PRICE_ID, BigDecimal.ZERO, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(planPriceRepository, never()).save(any());
        }

        @Test
        @DisplayName("identity fields — planId, cycle, currency, region, effectiveFrom never changed")
        void update_immutableFieldsPreserved() {
            PlanPrice existing = buildPrice();

            when(planPriceRepository.findById(PRICE_ID)).thenReturn(Optional.of(existing));
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.updatePrice(ACTOR_ID, PRICE_ID, new BigDecimal("599"), null, null);

            verify(planPriceRepository).save(captor.capture());
            PlanPrice saved = captor.getValue();
            assertThat(saved.getPlanId()).isEqualTo(existing.getPlanId());
            assertThat(saved.getCycle()).isEqualTo(existing.getCycle());
            assertThat(saved.getCurrency()).isEqualTo(existing.getCurrency());
            assertThat(saved.getRegion()).isEqualTo(existing.getRegion());
            assertThat(saved.getEffectiveFrom()).isEqualTo(existing.getEffectiveFrom());
        }

        @Test
        @DisplayName("audit — createdAt/createdBy preserved; updatedAt/updatedBy refreshed")
        void update_auditFieldsPreservedAndRefreshed() {
            UUID originalCreator = UUID.fromString("00000000-0000-0000-0000-000000000099");
            Instant originalCreatedAt = Instant.now().minusSeconds(60);
            PlanPrice existing = PlanPrice.builder()
                .id(PRICE_ID).version(0L)
                .planId(PLAN_ID)
                .region("INDIA").currency("INR")
                .cycle(BillingCycle.MONTHLY)
                .amount(new BigDecimal("499"))
                .taxInclusive(false).effectiveFrom(EFFECTIVE).active(true)
                .createdAt(originalCreatedAt).updatedAt(originalCreatedAt)
                .createdBy(originalCreator).updatedBy(originalCreator)
                .build();

            when(planPriceRepository.findById(PRICE_ID)).thenReturn(Optional.of(existing));
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.updatePrice(ACTOR_ID, PRICE_ID, new BigDecimal("599"), null, null);

            verify(planPriceRepository).save(captor.capture());
            PlanPrice saved = captor.getValue();
            assertThat(saved.getCreatedAt()).isEqualTo(originalCreatedAt);   // preserved
            assertThat(saved.getCreatedBy()).isEqualTo(originalCreator);     // preserved
            assertThat(saved.getUpdatedAt()).isAfter(originalCreatedAt);     // refreshed
            assertThat(saved.getUpdatedBy()).isEqualTo(ACTOR_ID);            // refreshed
        }

        @Test
        @DisplayName("null fields leave existing values unchanged")
        void update_nullFieldsPreserveExisting() {
            PlanPrice existing = buildPrice();

            when(planPriceRepository.findById(PRICE_ID)).thenReturn(Optional.of(existing));
            when(planPriceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PlanPrice> captor = ArgumentCaptor.forClass(PlanPrice.class);
            service.updatePrice(ACTOR_ID, PRICE_ID, null, null, null);

            verify(planPriceRepository).save(captor.capture());
            PlanPrice saved = captor.getValue();
            assertThat(saved.getAmount()).isEqualByComparingTo(existing.getAmount());
            assertThat(saved.isTaxInclusive()).isEqualTo(existing.isTaxInclusive());
            assertThat(saved.isActive()).isEqualTo(existing.isActive());
        }
    }

    // ── getPrice ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPrice()")
    class GetPrice {

        @Test
        @DisplayName("success — returns price when it exists")
        void get_success() {
            PlanPrice price = buildPrice();
            when(planPriceRepository.findById(PRICE_ID)).thenReturn(Optional.of(price));

            PlanPrice result = service.getPrice(PRICE_ID);

            assertThat(result.getId()).isEqualTo(PRICE_ID);
        }

        @Test
        @DisplayName("not found — throws PLAN_PRICE_NOT_FOUND")
        void get_notFound_throws() {
            when(planPriceRepository.findById(PRICE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPrice(PRICE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_PRICE_NOT_FOUND));
        }
    }

    // ── listPrices ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listPrices()")
    class ListPrices {

        private final PlanPrice indiaMonthly = buildPrice(
            UUID.randomUUID(), "INDIA", "INR",  BillingCycle.MONTHLY, new BigDecimal("499"), true);
        private final PlanPrice indiaAnnual  = buildPrice(
            UUID.randomUUID(), "INDIA", "INR",  BillingCycle.ANNUAL,  new BigDecimal("4990"), true);
        private final PlanPrice usMonthly    = buildPrice(
            UUID.randomUUID(), "US",    "USD",  BillingCycle.MONTHLY, new BigDecimal("9.99"), true);
        private final PlanPrice euInactive   = buildPrice(
            UUID.randomUUID(), "EU",    "EUR",  BillingCycle.ANNUAL,  new BigDecimal("89"), false);

        @Test
        @DisplayName("planId=null, all filters null — returns all non-deleted prices")
        void list_all_returnsAll() {
            when(planPriceRepository.findAll())
                .thenReturn(List.of(indiaMonthly, indiaAnnual, usMonthly, euInactive));

            List<PlanPrice> result = service.listPrices(null, null, null, null, null);

            assertThat(result).hasSize(4);
        }

        @Test
        @DisplayName("planId set — delegates to findByPlanId")
        void list_planFilter_delegatesToFindByPlanId() {
            when(planPriceRepository.findByPlanId(PLAN_ID))
                .thenReturn(List.of(indiaMonthly, indiaAnnual));

            List<PlanPrice> result = service.listPrices(PLAN_ID, null, null, null, null);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("region filter — returns only prices matching given region")
        void list_regionFilter_returnsMatchingOnly() {
            when(planPriceRepository.findAll())
                .thenReturn(List.of(indiaMonthly, indiaAnnual, usMonthly));

            List<PlanPrice> result = service.listPrices(null, "INDIA", null, null, null);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(p -> p.getRegion().equals("INDIA"));
        }

        @Test
        @DisplayName("currency filter — returns only prices matching given currency")
        void list_currencyFilter_returnsMatchingOnly() {
            when(planPriceRepository.findAll())
                .thenReturn(List.of(indiaMonthly, indiaAnnual, usMonthly));

            List<PlanPrice> result = service.listPrices(null, null, "USD", null, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCurrency()).isEqualTo("USD");
        }

        @Test
        @DisplayName("cycle filter — returns only prices matching given cycle")
        void list_cycleFilter_returnsMatchingOnly() {
            when(planPriceRepository.findAll())
                .thenReturn(List.of(indiaMonthly, indiaAnnual, usMonthly));

            List<PlanPrice> result = service.listPrices(null, null, null, BillingCycle.ANNUAL, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCycle()).isEqualTo(BillingCycle.ANNUAL);
        }

        @Test
        @DisplayName("active=false filter — returns only inactive prices")
        void list_activeFilter_returnsOnlyInactive() {
            when(planPriceRepository.findAll())
                .thenReturn(List.of(indiaMonthly, indiaAnnual, usMonthly, euInactive));

            List<PlanPrice> result = service.listPrices(null, null, null, null, false);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).isActive()).isFalse();
        }

        @Test
        @DisplayName("combined filters — region + currency + cycle narrows to single result")
        void list_combinedFilters_returnsNarrowedResult() {
            when(planPriceRepository.findAll())
                .thenReturn(List.of(indiaMonthly, indiaAnnual, usMonthly, euInactive));

            List<PlanPrice> result = service.listPrices(null, "INDIA", "INR", BillingCycle.ANNUAL, null);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCycle()).isEqualTo(BillingCycle.ANNUAL);
            assertThat(result.get(0).getRegion()).isEqualTo("INDIA");
        }

        @Test
        @DisplayName("empty catalog — returns empty list")
        void list_emptyCatalog_returnsEmptyList() {
            when(planPriceRepository.findAll()).thenReturn(List.of());

            List<PlanPrice> result = service.listPrices(null, null, null, null, null);

            assertThat(result).isEmpty();
        }
    }

    // ── deletePrice ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deletePrice()")
    class DeletePrice {

        @Test
        @DisplayName("success — delegates to softDelete with actorId")
        void delete_success() {
            service.deletePrice(ACTOR_ID, PRICE_ID);

            verify(planPriceRepository).softDelete(PRICE_ID, ACTOR_ID);
        }

        @Test
        @DisplayName("not found — ResourceNotFoundException propagates from port")
        void delete_notFound_throws() {
            Mockito.doThrow(new ResourceNotFoundException(
                    ErrorCode.PLAN_PRICE_NOT_FOUND, "not found"))
                .when(planPriceRepository).softDelete(PRICE_ID, ACTOR_ID);

            assertThatThrownBy(() -> service.deletePrice(ACTOR_ID, PRICE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PLAN_PRICE_NOT_FOUND));
        }
    }
}
