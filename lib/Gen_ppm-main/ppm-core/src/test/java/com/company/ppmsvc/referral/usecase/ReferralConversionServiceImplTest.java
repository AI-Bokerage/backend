package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.coupon.model.Coupon;
import com.company.ppmsvc.coupon.port.CouponRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import com.company.ppmsvc.promotion.model.ReferralEventStatus;
import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import com.company.ppmsvc.referral.model.ReferralCode;
import com.company.ppmsvc.referral.model.ReferralEvent;
import com.company.ppmsvc.referral.model.ReferralProgram;
import com.company.ppmsvc.referral.model.ReferralReward;
import com.company.ppmsvc.referral.port.ReferralCodeRepositoryPort;
import com.company.ppmsvc.referral.port.ReferralEventRepositoryPort;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
import java.time.Instant;
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
@DisplayName("ReferralConversionServiceImpl")
class ReferralConversionServiceImplTest {

    static final UUID CODE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PROGRAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID REFERRER_REWARD_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock ReferralCodeRepositoryPort    referralCodeRepository;
    @Mock ReferralProgramRepositoryPort referralProgramRepository;
    @Mock ReferralEventRepositoryPort   referralEventRepository;
    @Mock CouponRepositoryPort          couponRepository;

    @InjectMocks ReferralConversionServiceImpl service;

    private ReferralCode buildCode() {
        Instant now = Instant.now();
        return ReferralCode.builder()
            .id(CODE_ID).version(0L)
            .code("REFER-NAMAN").referralProgramId(PROGRAM_ID).referrerCustomerId("referrer-123")
            .status(ReferralCodeStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .build();
    }

    private ReferralProgram buildProgram(Integer maxReferralsPerReferrer) {
        Instant now = Instant.now();
        return ReferralProgram.builder()
            .id(PROGRAM_ID).version(0L)
            .name("Refer a friend")
            .referrerRewardPromotionId(REFERRER_REWARD_ID)
            .referredRewardPromotionId(UUID.randomUUID())
            .status(ReferralProgramStatus.ACTIVE)
            .maxReferralsPerReferrer(maxReferralsPerReferrer)
            .createdAt(now).updatedAt(now)
            .build();
    }

    @Nested
    @DisplayName("happy path")
    class HappyPath {

        @Test
        @DisplayName("creates event with rewardGrantedAt set, issues coupon, returns ReferralReward")
        void onConversion_success() {
            ReferralCode code = buildCode();
            ReferralProgram program = buildProgram(null);
            when(referralCodeRepository.findByCode("REFER-NAMAN")).thenReturn(Optional.of(code));
            when(referralProgramRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program));
            when(referralEventRepository.findByCodeAndCustomer(CODE_ID, "referred-1")).thenReturn(Optional.empty());
            when(referralEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ReferralReward reward = service.onConversion("REFER-NAMAN", "referred-1");

            assertThat(reward).isNotNull();
            assertThat(reward.promotionId()).isEqualTo(REFERRER_REWARD_ID);
            assertThat(reward.customerId()).isEqualTo("referrer-123");
            assertThat(reward.couponCode()).isNotBlank();

            ArgumentCaptor<ReferralEvent> eventCaptor = ArgumentCaptor.forClass(ReferralEvent.class);
            verify(referralEventRepository, org.mockito.Mockito.times(2)).save(eventCaptor.capture());
            ReferralEvent grantedEvent = eventCaptor.getAllValues().get(1);
            assertThat(grantedEvent.getStatus()).isEqualTo(ReferralEventStatus.CONVERTED);
            assertThat(grantedEvent.getRewardGrantedAt()).isNotNull();

            ArgumentCaptor<Coupon> couponCaptor = ArgumentCaptor.forClass(Coupon.class);
            verify(couponRepository).save(couponCaptor.capture());
            Coupon issuedCoupon = couponCaptor.getValue();
            assertThat(issuedCoupon.getCode()).isEqualTo(issuedCoupon.getCode().toUpperCase());
            assertThat(issuedCoupon.getCode()).startsWith("REFRR-REFERRER-");
            assertThat(issuedCoupon.getPromotionId()).isEqualTo(REFERRER_REWARD_ID);
            assertThat(issuedCoupon.getActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("idempotency")
    class Idempotency {

        @Test
        @DisplayName("already converted for this customer — throws REFERRAL_ALREADY_CONVERTED")
        void onConversion_alreadyConverted_throws() {
            ReferralCode code = buildCode();
            ReferralProgram program = buildProgram(null);
            Instant now = Instant.now();
            ReferralEvent existing = ReferralEvent.builder()
                .id(UUID.randomUUID())
                .referralCodeId(CODE_ID).referredCustomerId("referred-1")
                .status(ReferralEventStatus.CONVERTED).convertedAt(now)
                .createdAt(now).updatedAt(now)
                .build();

            when(referralCodeRepository.findByCode("REFER-NAMAN")).thenReturn(Optional.of(code));
            when(referralProgramRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program));
            when(referralEventRepository.findByCodeAndCustomer(CODE_ID, "referred-1")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> service.onConversion("REFER-NAMAN", "referred-1"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REFERRAL_ALREADY_CONVERTED));

            verify(referralEventRepository, never()).save(any());
            verify(couponRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("cap enforcement")
    class CapEnforcement {

        @Test
        @DisplayName("referrer cap reached — throws REFERRAL_CAP_REACHED")
        void onConversion_capReached_throws() {
            ReferralCode code = buildCode();
            ReferralProgram program = buildProgram(2);

            when(referralCodeRepository.findByCode("REFER-NAMAN")).thenReturn(Optional.of(code));
            when(referralProgramRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(program));
            when(referralEventRepository.findByCodeAndCustomer(CODE_ID, "referred-9")).thenReturn(Optional.empty());
            when(referralEventRepository.countConvertedByCode(CODE_ID)).thenReturn(2);

            assertThatThrownBy(() -> service.onConversion("REFER-NAMAN", "referred-9"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REFERRAL_CAP_REACHED));

            verify(referralEventRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("not found paths")
    class NotFound {

        @Test
        @DisplayName("unknown referral code — throws ResourceNotFoundException REFERRAL_CODE_NOT_FOUND")
        void onConversion_unknownCode_throws() {
            when(referralCodeRepository.findByCode("MISSING")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.onConversion("MISSING", "referred-1"))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REFERRAL_CODE_NOT_FOUND));
        }
    }
}
