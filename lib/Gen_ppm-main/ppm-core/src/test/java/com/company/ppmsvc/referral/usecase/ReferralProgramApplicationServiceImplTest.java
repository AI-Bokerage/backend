package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import com.company.ppmsvc.referral.model.ReferralProgram;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReferralProgramApplicationServiceImpl")
class ReferralProgramApplicationServiceImplTest {

    static final UUID ACTOR_ID     = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PROGRAM_ID   = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID REFERRER_REWARD_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final UUID REFERRED_REWARD_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Mock ReferralProgramRepositoryPort programRepository;
    @Mock PromotionRepositoryPort       promotionRepository;

    @InjectMocks ReferralProgramApplicationServiceImpl service;

    private Promotion dummyPromotion(UUID id) {
        Instant now = Instant.now();
        return Promotion.builder()
            .id(id).version(0L)
            .name("x").action(new FlatDiscount(new BigDecimal("10")))
            .validFrom(LocalDate.of(2025, 1, 1)).validUntil(LocalDate.of(2025, 12, 31))
            .status(PromotionStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .build();
    }

    @Nested
    @DisplayName("createProgram()")
    class CreateProgram {

        @Test
        @DisplayName("success — both reward promotions exist, saves and returns")
        void create_success() {
            when(promotionRepository.findById(REFERRER_REWARD_ID)).thenReturn(Optional.of(dummyPromotion(REFERRER_REWARD_ID)));
            when(promotionRepository.findById(REFERRED_REWARD_ID)).thenReturn(Optional.of(dummyPromotion(REFERRED_REWARD_ID)));
            when(programRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReferralProgram result = service.createProgram(ACTOR_ID, "Refer a friend", null,
                REFERRER_REWARD_ID, REFERRED_REWARD_ID, null, null);

            assertThat(result.getStatus()).isEqualTo(ReferralProgramStatus.ACTIVE);
            verify(programRepository).save(any());
        }

        @Test
        @DisplayName("missing referrer reward promotion — throws PROMOTION_NOT_FOUND")
        void create_missingReferrerReward_throws() {
            when(promotionRepository.findById(REFERRER_REWARD_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createProgram(ACTOR_ID, "Refer a friend", null,
                    REFERRER_REWARD_ID, REFERRED_REWARD_ID, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMOTION_NOT_FOUND));

            verify(programRepository, never()).save(any());
        }

        @Test
        @DisplayName("missing referred reward promotion — throws PROMOTION_NOT_FOUND")
        void create_missingReferredReward_throws() {
            when(promotionRepository.findById(REFERRER_REWARD_ID)).thenReturn(Optional.of(dummyPromotion(REFERRER_REWARD_ID)));
            when(promotionRepository.findById(REFERRED_REWARD_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createProgram(ACTOR_ID, "Refer a friend", null,
                    REFERRER_REWARD_ID, REFERRED_REWARD_ID, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PROMOTION_NOT_FOUND));

            verify(programRepository, never()).save(any());
        }

        @Test
        @DisplayName("maxReferralsPerReferrer = 0 — throws VALIDATION_ERROR")
        void create_invalidMaxReferrals_throws() {
            when(promotionRepository.findById(REFERRER_REWARD_ID)).thenReturn(Optional.of(dummyPromotion(REFERRER_REWARD_ID)));
            when(promotionRepository.findById(REFERRED_REWARD_ID)).thenReturn(Optional.of(dummyPromotion(REFERRED_REWARD_ID)));

            assertThatThrownBy(() -> service.createProgram(ACTOR_ID, "Refer a friend", null,
                    REFERRER_REWARD_ID, REFERRED_REWARD_ID, null, 0))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_ERROR));

            verify(programRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateProgram()")
    class UpdateProgram {

        @Test
        @DisplayName("not found — throws REFERRAL_PROGRAM_NOT_FOUND")
        void update_notFound_throws() {
            when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateProgram(ACTOR_ID, PROGRAM_ID, "X", null, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REFERRAL_PROGRAM_NOT_FOUND));
        }
    }
}
