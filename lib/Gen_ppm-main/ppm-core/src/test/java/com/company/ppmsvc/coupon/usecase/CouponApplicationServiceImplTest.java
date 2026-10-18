package com.company.ppmsvc.coupon.usecase;

import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponApplicationServiceImpl")
class CouponApplicationServiceImplTest {

    static final UUID ACTOR_ID     = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID COUPON_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID PROMOTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock CouponRepositoryPort couponRepository;
    @Mock PromotionRepositoryPort promotionRepository;

    @InjectMocks CouponApplicationServiceImpl service;

    private Coupon buildCoupon(String code, boolean active) {
        Instant now = Instant.now();
        return Coupon.builder()
            .id(COUPON_ID).version(0L)
            .code(code).promotionId(PROMOTION_ID).active(active)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    private Promotion buildPromotion() {
        Instant now = Instant.now();
        return Promotion.builder()
            .id(PROMOTION_ID).version(0L)
            .name("Sale").action(new FlatDiscount(new BigDecimal("10")))
            .validFrom(LocalDate.of(2025, 1, 1)).validUntil(LocalDate.of(2025, 12, 31))
            .status(PromotionStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    @Nested
    @DisplayName("createCoupon()")
    class CreateCoupon {

        @Test
        @DisplayName("success — saves coupon and returns it")
        void create_success() {
            when(couponRepository.existsByCode("DIWALI20")).thenReturn(false);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(buildPromotion()));
            Coupon saved = buildCoupon("DIWALI20", true);
            when(couponRepository.save(any())).thenReturn(saved);

            Coupon result = service.createCoupon(ACTOR_ID, "DIWALI20", PROMOTION_ID, null);

            assertThat(result.getId()).isEqualTo(COUPON_ID);
            verify(couponRepository).save(any());
        }

        @Test
        @DisplayName("code normalised — trimmed and uppercased before uniqueness check and persistence")
        void create_codeNormalized() {
            when(couponRepository.existsByCode("DIWALI20")).thenReturn(false);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(buildPromotion()));
            when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
            service.createCoupon(ACTOR_ID, "  diwali20  ", PROMOTION_ID, null);

            verify(couponRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo("DIWALI20");
        }

        @Test
        @DisplayName("duplicate code throws COUPON_CODE_ALREADY_EXISTS")
        void create_duplicateCode_throws() {
            when(couponRepository.existsByCode("DIWALI20")).thenReturn(true);

            assertThatThrownBy(() -> service.createCoupon(ACTOR_ID, "DIWALI20", PROMOTION_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_CODE_ALREADY_EXISTS));

            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("missing promotion throws PROMOTION_NOT_FOUND")
        void create_promotionMissing_throws() {
            when(couponRepository.existsByCode("DIWALI20")).thenReturn(false);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createCoupon(ACTOR_ID, "DIWALI20", PROMOTION_ID, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMOTION_NOT_FOUND));

            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("active defaults to true when null")
        void create_activeDefaultsTrue() {
            when(couponRepository.existsByCode("DIWALI20")).thenReturn(false);
            when(promotionRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(buildPromotion()));
            when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
            service.createCoupon(ACTOR_ID, "DIWALI20", PROMOTION_ID, null);

            verify(couponRepository).save(captor.capture());
            assertThat(captor.getValue().getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("updateCoupon()")
    class UpdateCoupon {

        @Test
        @DisplayName("success — updates active flag; code stays immutable")
        void update_success_codeImmutable() {
            Coupon existing = buildCoupon("DIWALI20", true);
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(existing));
            when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
            service.updateCoupon(ACTOR_ID, COUPON_ID, null, false);

            verify(couponRepository).save(captor.capture());
            Coupon saved = captor.getValue();
            assertThat(saved.getCode()).isEqualTo("DIWALI20");
            assertThat(saved.getActive()).isFalse();
        }

        @Test
        @DisplayName("not found — throws COUPON_NOT_FOUND")
        void update_notFound_throws() {
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCoupon(ACTOR_ID, COUPON_ID, null, false))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.COUPON_NOT_FOUND));

            verify(couponRepository, never()).save(any());
        }

        @Test
        @DisplayName("changing promotionId re-verifies the new promotion exists")
        void update_newPromotionMissing_throws() {
            UUID newPromotionId = UUID.randomUUID();
            Coupon existing = buildCoupon("DIWALI20", true);
            when(couponRepository.findById(COUPON_ID)).thenReturn(Optional.of(existing));
            when(promotionRepository.findById(newPromotionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCoupon(ACTOR_ID, COUPON_ID, newPromotionId, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMOTION_NOT_FOUND));

            verify(couponRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteCoupon()")
    class DeleteCoupon {

        @Test
        @DisplayName("success — delegates to softDelete with given actor")
        void delete_success() {
            service.deleteCoupon(ACTOR_ID, COUPON_ID);

            verify(couponRepository).softDelete(COUPON_ID, ACTOR_ID);
        }
    }

    @Nested
    @DisplayName("getCouponByCode()")
    class GetCouponByCode {

        @Test
        @DisplayName("existing code returns Optional.of(coupon)")
        void getByCode_existing_returnsPresent() {
            when(couponRepository.findByCode("DIWALI20")).thenReturn(Optional.of(buildCoupon("DIWALI20", true)));

            assertThat(service.getCouponByCode("DIWALI20")).isPresent();
        }

        @Test
        @DisplayName("missing code returns Optional.empty()")
        void getByCode_missing_returnsEmpty() {
            when(couponRepository.findByCode("MISSING")).thenReturn(Optional.empty());

            assertThat(service.getCouponByCode("MISSING")).isEmpty();
        }
    }
}
