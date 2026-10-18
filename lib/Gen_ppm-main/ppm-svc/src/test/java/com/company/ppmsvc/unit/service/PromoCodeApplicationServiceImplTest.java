package com.company.ppmsvc.unit.service;

import com.company.ppmsvc.promocode.usecase.PromoCodeApplicationServiceImpl;
import com.company.ppmsvc.promocode.model.DiscountType;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
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
@DisplayName("PromoCodeApplicationServiceImpl")
class PromoCodeApplicationServiceImplTest {

    static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID CODE_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    static final LocalDate VALID_FROM  = LocalDate.of(2025, 1, 1);
    static final LocalDate VALID_UNTIL = LocalDate.of(2025, 12, 31);

    @Mock PromoCodeRepositoryPort promoCodeRepository;

    @InjectMocks PromoCodeApplicationServiceImpl service;

    // ── fixtures ──────────────────────────────────────────────────────────────

    private PromoCode buildPromoCode(String code, DiscountType type, BigDecimal value,
                                     boolean firstTimeOnly, boolean active) {
        Instant now = Instant.now();
        return PromoCode.builder()
            .id(CODE_ID).version(0L)
            .code(code)
            .discountType(type).value(value)
            .validFrom(VALID_FROM).validUntil(VALID_UNTIL)
            .usageCap(null).usageCount(0)
            .firstTimeOnly(firstTimeOnly).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private PromoCode buildPromoCode() {
        return buildPromoCode("SUMMER20", DiscountType.PERCENTAGE, new BigDecimal("20"), false, true);
    }

    // ── createPromoCode ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("createPromoCode()")
    class CreatePromoCode {

        @Test
        @DisplayName("success — saves promo code and returns it")
        void create_success() {
            when(promoCodeRepository.existsByCode("SUMMER20")).thenReturn(false);
            PromoCode saved = buildPromoCode();
            when(promoCodeRepository.save(any())).thenReturn(saved);

            PromoCode result = service.createPromoCode(ACTOR_ID, "SUMMER20", DiscountType.PERCENTAGE,
                new BigDecimal("20"), VALID_FROM, VALID_UNTIL, null, null, null);

            assertThat(result.getId()).isEqualTo(CODE_ID);
            verify(promoCodeRepository).save(any());
        }

        @Test
        @DisplayName("BR-1 — duplicate code throws PROMO_CODE_ALREADY_EXISTS")
        void create_duplicateCode_throws() {
            when(promoCodeRepository.existsByCode("SUMMER20")).thenReturn(true);

            assertThatThrownBy(() -> service.createPromoCode(ACTOR_ID, "SUMMER20", DiscountType.PERCENTAGE,
                    new BigDecimal("20"), VALID_FROM, VALID_UNTIL, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMO_CODE_ALREADY_EXISTS));

            verify(promoCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-2 — code trimmed and uppercased before uniqueness check and persistence")
        void create_codeNormalized() {
            when(promoCodeRepository.existsByCode("PROMO10")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
            service.createPromoCode(ACTOR_ID, "  promo10  ", DiscountType.FLAT, new BigDecimal("10"),
                VALID_FROM, VALID_UNTIL, null, null, null);

            verify(promoCodeRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo("PROMO10");
        }

        @Test
        @DisplayName("BR-3 — PERCENTAGE value of exactly 100 is accepted")
        void create_percentage100_accepted() {
            when(promoCodeRepository.existsByCode("HUNDRED")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createPromoCode(ACTOR_ID, "HUNDRED", DiscountType.PERCENTAGE, new BigDecimal("100"),
                VALID_FROM, VALID_UNTIL, null, null, null);

            verify(promoCodeRepository).save(any());
        }

        @Test
        @DisplayName("BR-3 — PERCENTAGE value > 100 throws VALIDATION_ERROR")
        void create_percentage101_throws() {
            when(promoCodeRepository.existsByCode("OVER")).thenReturn(false);

            assertThatThrownBy(() -> service.createPromoCode(ACTOR_ID, "OVER", DiscountType.PERCENTAGE,
                    new BigDecimal("101"), VALID_FROM, VALID_UNTIL, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promoCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-3 — PERCENTAGE value = 0 throws VALIDATION_ERROR")
        void create_percentage0_throws() {
            when(promoCodeRepository.existsByCode("ZERO")).thenReturn(false);

            assertThatThrownBy(() -> service.createPromoCode(ACTOR_ID, "ZERO", DiscountType.PERCENTAGE,
                    BigDecimal.ZERO, VALID_FROM, VALID_UNTIL, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promoCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-3 — FLAT value = 0 throws VALIDATION_ERROR")
        void create_flat0_throws() {
            when(promoCodeRepository.existsByCode("FLAT0")).thenReturn(false);

            assertThatThrownBy(() -> service.createPromoCode(ACTOR_ID, "FLAT0", DiscountType.FLAT,
                    BigDecimal.ZERO, VALID_FROM, VALID_UNTIL, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promoCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-4 — validUntil before validFrom throws VALIDATION_ERROR")
        void create_invalidDateRange_throws() {
            when(promoCodeRepository.existsByCode("BADDATE")).thenReturn(false);

            assertThatThrownBy(() -> service.createPromoCode(ACTOR_ID, "BADDATE", DiscountType.FLAT,
                    new BigDecimal("10"), LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1),
                    null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promoCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-4 — same-day validity (validFrom == validUntil) is accepted")
        void create_sameDayValidity_accepted() {
            LocalDate sameDay = LocalDate.of(2025, 6, 1);
            when(promoCodeRepository.existsByCode("SAMEDAY")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.createPromoCode(ACTOR_ID, "SAMEDAY", DiscountType.FLAT, new BigDecimal("5"),
                sameDay, sameDay, null, null, null);

            verify(promoCodeRepository).save(any());
        }

        @Test
        @DisplayName("BR-5 — firstTimeOnly defaults to false, active to true, usageCount to 0")
        void create_defaults() {
            when(promoCodeRepository.existsByCode("DEFAULTS")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
            service.createPromoCode(ACTOR_ID, "DEFAULTS", DiscountType.FLAT, new BigDecimal("10"),
                VALID_FROM, VALID_UNTIL, null, null, null);

            verify(promoCodeRepository).save(captor.capture());
            PromoCode saved = captor.getValue();
            assertThat(saved.getFirstTimeOnly()).isFalse();
            assertThat(saved.getActive()).isTrue();
            assertThat(saved.getUsageCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("BR-7 — createdBy and updatedBy set from given actor")
        void create_auditFieldsSetFromActor() {
            when(promoCodeRepository.existsByCode("AUDIT")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
            service.createPromoCode(ACTOR_ID, "AUDIT", DiscountType.FLAT, new BigDecimal("10"),
                VALID_FROM, VALID_UNTIL, null, null, null);

            verify(promoCodeRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);
            assertThat(captor.getValue().getUpdatedBy()).isEqualTo(ACTOR_ID);
        }

        @Test
        @DisplayName("C3 — future validity window is accepted with no restriction")
        void create_futureValidityWindow_accepted() {
            LocalDate futureFrom  = LocalDate.of(2030, 1, 1);
            LocalDate futureUntil = LocalDate.of(2030, 12, 31);
            when(promoCodeRepository.existsByCode("FUTURE30")).thenReturn(false);
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
            service.createPromoCode(ACTOR_ID, "FUTURE30", DiscountType.FLAT, new BigDecimal("5"),
                futureFrom, futureUntil, null, null, null);

            verify(promoCodeRepository).save(captor.capture());
            assertThat(captor.getValue().getValidFrom()).isEqualTo(futureFrom);
            assertThat(captor.getValue().getValidUntil()).isEqualTo(futureUntil);
        }
    }

    // ── updatePromoCode ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("updatePromoCode()")
    class UpdatePromoCode {

        @Test
        @DisplayName("success — updates mutable fields and returns promo code")
        void update_success() {
            PromoCode existing = buildPromoCode();
            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.of(existing));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PromoCode result = service.updatePromoCode(ACTOR_ID, CODE_ID, null, new BigDecimal("30"),
                null, null, null, null, null);

            assertThat(result.getValue()).isEqualByComparingTo(new BigDecimal("30"));
            verify(promoCodeRepository).save(any());
        }

        @Test
        @DisplayName("not found — throws PROMO_CODE_NOT_FOUND")
        void update_notFound_throws() {
            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updatePromoCode(ACTOR_ID, CODE_ID, null,
                    new BigDecimal("30"), null, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMO_CODE_NOT_FOUND));

            verify(promoCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-6 — id, code, createdAt, createdBy, usageCount always preserved")
        void update_immutableFieldsPreserved() {
            PromoCode existing = buildPromoCode();
            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.of(existing));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
            service.updatePromoCode(ACTOR_ID, CODE_ID, null, new BigDecimal("25"),
                null, null, null, null, null);

            verify(promoCodeRepository).save(captor.capture());
            PromoCode saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(existing.getId());
            assertThat(saved.getCode()).isEqualTo(existing.getCode());
            assertThat(saved.getCreatedAt()).isEqualTo(existing.getCreatedAt());
            assertThat(saved.getCreatedBy()).isEqualTo(existing.getCreatedBy());
            assertThat(saved.getUsageCount()).isEqualTo(existing.getUsageCount());
        }

        @Test
        @DisplayName("BR-7 — updatedBy refreshed; updatedAt advances")
        void update_auditRefreshed() {
            UUID originalCreator = UUID.fromString("00000000-0000-0000-0000-000000000099");
            Instant originalCreatedAt = Instant.now().minusSeconds(60);
            PromoCode existing = PromoCode.builder()
                .id(CODE_ID).version(0L)
                .code("SUMMER20").discountType(DiscountType.PERCENTAGE)
                .value(new BigDecimal("20"))
                .validFrom(VALID_FROM).validUntil(VALID_UNTIL)
                .usageCap(null).usageCount(0)
                .firstTimeOnly(false).active(true)
                .createdAt(originalCreatedAt).updatedAt(originalCreatedAt)
                .createdBy(originalCreator).updatedBy(originalCreator)
                .build();

            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.of(existing));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
            service.updatePromoCode(ACTOR_ID, CODE_ID, null, new BigDecimal("25"),
                null, null, null, null, null);

            verify(promoCodeRepository).save(captor.capture());
            PromoCode saved = captor.getValue();
            assertThat(saved.getCreatedAt()).isEqualTo(originalCreatedAt);  // preserved
            assertThat(saved.getCreatedBy()).isEqualTo(originalCreator);    // preserved
            assertThat(saved.getUpdatedAt()).isAfter(originalCreatedAt);    // refreshed
            assertThat(saved.getUpdatedBy()).isEqualTo(ACTOR_ID);           // refreshed
        }

        @Test
        @DisplayName("BR-3 — updating to invalid percentage value throws VALIDATION_ERROR")
        void update_invalidPercentage_throws() {
            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.of(buildPromoCode()));

            assertThatThrownBy(() -> service.updatePromoCode(ACTOR_ID, CODE_ID, null,
                    new BigDecimal("101"), null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promoCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("BR-4 — updating to invalid date range throws VALIDATION_ERROR")
        void update_invalidDateRange_throws() {
            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.of(buildPromoCode()));

            assertThatThrownBy(() -> service.updatePromoCode(ACTOR_ID, CODE_ID, null, null,
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), null, null, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(promoCodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("null arguments leave existing values unchanged")
        void update_nullFieldsPreserveExisting() {
            PromoCode existing = buildPromoCode();
            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.of(existing));
            when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
            service.updatePromoCode(ACTOR_ID, CODE_ID, null, null, null, null, null, null, null);

            verify(promoCodeRepository).save(captor.capture());
            PromoCode saved = captor.getValue();
            assertThat(saved.getDiscountType()).isEqualTo(existing.getDiscountType());
            assertThat(saved.getValue()).isEqualByComparingTo(existing.getValue());
            assertThat(saved.getValidFrom()).isEqualTo(existing.getValidFrom());
            assertThat(saved.getValidUntil()).isEqualTo(existing.getValidUntil());
            assertThat(saved.getActive()).isEqualTo(existing.getActive());
        }
    }

    // ── getPromoCode ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPromoCode()")
    class GetPromoCode {

        @Test
        @DisplayName("success — returns promo code when it exists")
        void get_success() {
            PromoCode pc = buildPromoCode();
            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.of(pc));

            PromoCode result = service.getPromoCode(CODE_ID);

            assertThat(result.getId()).isEqualTo(CODE_ID);
        }

        @Test
        @DisplayName("not found — throws PROMO_CODE_NOT_FOUND")
        void get_notFound_throws() {
            when(promoCodeRepository.findById(CODE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getPromoCode(CODE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMO_CODE_NOT_FOUND));
        }
    }

    // ── getPromoCodeByCode ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getPromoCodeByCode()")
    class GetPromoCodeByCode {

        @Test
        @DisplayName("K1 — existing code returns Optional.of(promoCode)")
        void getByCode_existing_returnsPresent() {
            PromoCode pc = buildPromoCode();
            when(promoCodeRepository.findByCode("SUMMER20")).thenReturn(Optional.of(pc));

            Optional<PromoCode> result = service.getPromoCodeByCode("SUMMER20");

            assertThat(result).isPresent();
            assertThat(result.get().getCode()).isEqualTo("SUMMER20");
        }

        @Test
        @DisplayName("K2 — missing code returns Optional.empty()")
        void getByCode_missing_returnsEmpty() {
            when(promoCodeRepository.findByCode("MISSING")).thenReturn(Optional.empty());

            Optional<PromoCode> result = service.getPromoCodeByCode("MISSING");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("K3 — soft-deleted code returns Optional.empty (repository hides deleted rows via @SQLRestriction)")
        void getByCode_softDeleted_returnsEmpty() {
            when(promoCodeRepository.findByCode("DELETED")).thenReturn(Optional.empty());

            Optional<PromoCode> result = service.getPromoCodeByCode("DELETED");

            assertThat(result).isEmpty();
            verify(promoCodeRepository).findByCode("DELETED");
        }
    }

    // ── listPromoCodes ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listPromoCodes()")
    class ListPromoCodes {

        private final PromoCode activePct  = buildPromoCode("A_PCT",  DiscountType.PERCENTAGE, new BigDecimal("20"), false, true);
        private final PromoCode activeFlat = buildPromoCode("A_FLAT", DiscountType.FLAT,       new BigDecimal("10"), false, true);
        private final PromoCode inactive   = buildPromoCode("INACTIVE", DiscountType.PERCENTAGE, new BigDecimal("5"), false, false);

        @Test
        @DisplayName("no filters — returns all non-deleted promo codes")
        void list_noFilters_returnsAll() {
            when(promoCodeRepository.findAll()).thenReturn(List.of(activePct, activeFlat, inactive));

            List<PromoCode> result = service.listPromoCodes(null, null);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("active=true — returns only active promo codes")
        void list_activeOnly_returnsOnlyActive() {
            when(promoCodeRepository.findAll()).thenReturn(List.of(activePct, activeFlat, inactive));

            List<PromoCode> result = service.listPromoCodes(true, null);

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(PromoCode::getActive);
        }

        @Test
        @DisplayName("type filter — returns only FLAT codes")
        void list_typeFilter_returnsMatchingOnly() {
            when(promoCodeRepository.findAll()).thenReturn(List.of(activePct, activeFlat, inactive));

            List<PromoCode> result = service.listPromoCodes(null, DiscountType.FLAT);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getDiscountType()).isEqualTo(DiscountType.FLAT);
        }

        @Test
        @DisplayName("combined filter — active=true AND type=PERCENTAGE narrows correctly")
        void list_combinedFilter_narrowsCorrectly() {
            when(promoCodeRepository.findAll()).thenReturn(List.of(activePct, activeFlat, inactive));

            List<PromoCode> result = service.listPromoCodes(true, DiscountType.PERCENTAGE);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("A_PCT");
        }

        @Test
        @DisplayName("empty catalog — returns empty list")
        void list_empty_returnsEmptyList() {
            when(promoCodeRepository.findAll()).thenReturn(List.of());

            assertThat(service.listPromoCodes(null, null)).isEmpty();
        }
    }

    // ── deletePromoCode ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("deletePromoCode()")
    class DeletePromoCode {

        @Test
        @DisplayName("success — delegates to softDelete with given actor")
        void delete_success() {
            service.deletePromoCode(ACTOR_ID, CODE_ID);

            verify(promoCodeRepository).softDelete(CODE_ID, ACTOR_ID);
        }

        @Test
        @DisplayName("not found — ResourceNotFoundException propagates from port")
        void delete_notFound_throws() {
            Mockito.doThrow(new ResourceNotFoundException(
                    ErrorCode.PROMO_CODE_NOT_FOUND, "not found"))
                .when(promoCodeRepository).softDelete(CODE_ID, ACTOR_ID);

            assertThatThrownBy(() -> service.deletePromoCode(ACTOR_ID, CODE_ID))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMO_CODE_NOT_FOUND));
        }
    }
}
