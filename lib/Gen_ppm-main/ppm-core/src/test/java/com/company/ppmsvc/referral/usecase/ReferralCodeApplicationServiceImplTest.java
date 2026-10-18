package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import com.company.ppmsvc.referral.model.ReferralCode;
import com.company.ppmsvc.referral.model.ReferralProgram;
import com.company.ppmsvc.referral.port.ReferralCodeRepositoryPort;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
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
@DisplayName("ReferralCodeApplicationServiceImpl")
class ReferralCodeApplicationServiceImplTest {

    static final UUID ACTOR_ID   = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID CODE_ID    = UUID.fromString("00000000-0000-0000-0000-000000000002");
    static final UUID PROGRAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Mock ReferralCodeRepositoryPort    codeRepository;
    @Mock ReferralProgramRepositoryPort programRepository;

    @InjectMocks ReferralCodeApplicationServiceImpl service;

    private ReferralProgram dummyProgram() {
        Instant now = Instant.now();
        return ReferralProgram.builder()
            .id(PROGRAM_ID).version(0L)
            .name("Refer a friend")
            .referrerRewardPromotionId(UUID.randomUUID())
            .referredRewardPromotionId(UUID.randomUUID())
            .status(ReferralProgramStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .build();
    }

    private ReferralCode buildCode(String code) {
        Instant now = Instant.now();
        return ReferralCode.builder()
            .id(CODE_ID).version(0L)
            .code(code).referralProgramId(PROGRAM_ID).referrerCustomerId("referrer-1")
            .status(ReferralCodeStatus.ACTIVE)
            .createdAt(now).updatedAt(now)
            .createdBy(ACTOR_ID).updatedBy(ACTOR_ID)
            .build();
    }

    @Nested
    @DisplayName("createCode()")
    class CreateCode {

        @Test
        @DisplayName("code normalised — trimmed and uppercased before uniqueness check and persistence")
        void create_codeNormalized() {
            when(codeRepository.existsByCode("REFER-NAMAN")).thenReturn(false);
            when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(dummyProgram()));
            when(codeRepository.existsByProgramAndReferrer(PROGRAM_ID, "referrer-1")).thenReturn(false);
            when(codeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<ReferralCode> captor = ArgumentCaptor.forClass(ReferralCode.class);
            service.createCode(ACTOR_ID, "  refer-naman  ", PROGRAM_ID, "referrer-1", null);

            verify(codeRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo("REFER-NAMAN");
            assertThat(captor.getValue().getStatus()).isEqualTo(ReferralCodeStatus.ACTIVE);
        }

        @Test
        @DisplayName("duplicate code — throws REFERRAL_CODE_ALREADY_EXISTS")
        void create_duplicateCode_throws() {
            when(codeRepository.existsByCode("REFER-NAMAN")).thenReturn(true);

            assertThatThrownBy(() -> service.createCode(ACTOR_ID, "REFER-NAMAN", PROGRAM_ID, "referrer-1", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REFERRAL_CODE_ALREADY_EXISTS));

            verify(codeRepository, never()).save(any());
        }

        @Test
        @DisplayName("unknown program — throws REFERRAL_PROGRAM_NOT_FOUND")
        void create_unknownProgram_throws() {
            when(codeRepository.existsByCode("REFER-NAMAN")).thenReturn(false);
            when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createCode(ACTOR_ID, "REFER-NAMAN", PROGRAM_ID, "referrer-1", null))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REFERRAL_PROGRAM_NOT_FOUND));

            verify(codeRepository, never()).save(any());
        }

        @Test
        @DisplayName("one-per-program-referrer — second code for same (program, referrer) throws ILLEGAL_ARGUMENT")
        void create_duplicateProgramReferrer_throws() {
            when(codeRepository.existsByCode("REFER-2")).thenReturn(false);
            when(programRepository.findById(PROGRAM_ID)).thenReturn(Optional.of(dummyProgram()));
            when(codeRepository.existsByProgramAndReferrer(PROGRAM_ID, "referrer-1")).thenReturn(true);

            assertThatThrownBy(() -> service.createCode(ACTOR_ID, "REFER-2", PROGRAM_ID, "referrer-1", null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ILLEGAL_ARGUMENT));

            verify(codeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateCode()")
    class UpdateCode {

        @Test
        @DisplayName("success — code stays immutable, only status changes")
        void update_success_codeImmutable() {
            ReferralCode existing = buildCode("REFER-NAMAN");
            when(codeRepository.findById(CODE_ID)).thenReturn(Optional.of(existing));
            when(codeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ArgumentCaptor<ReferralCode> captor = ArgumentCaptor.forClass(ReferralCode.class);
            service.updateCode(ACTOR_ID, CODE_ID, ReferralCodeStatus.INACTIVE);

            verify(codeRepository).save(captor.capture());
            assertThat(captor.getValue().getCode()).isEqualTo("REFER-NAMAN");
            assertThat(captor.getValue().getStatus()).isEqualTo(ReferralCodeStatus.INACTIVE);
        }

        @Test
        @DisplayName("not found — throws REFERRAL_CODE_NOT_FOUND")
        void update_notFound_throws() {
            when(codeRepository.findById(CODE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateCode(ACTOR_ID, CODE_ID, ReferralCodeStatus.INACTIVE))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> assertThat(((ResourceNotFoundException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.REFERRAL_CODE_NOT_FOUND));
        }
    }
}
