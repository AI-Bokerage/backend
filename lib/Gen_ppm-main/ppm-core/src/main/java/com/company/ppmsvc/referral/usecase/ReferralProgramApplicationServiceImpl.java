package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import com.company.ppmsvc.referral.model.ReferralProgram;
import com.company.ppmsvc.referral.port.ReferralProgramRepositoryPort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReferralProgramApplicationServiceImpl implements ReferralProgramApplicationService {

    private final ReferralProgramRepositoryPort programRepository;
    private final PromotionRepositoryPort       promotionRepository;

    // ── createProgram ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReferralProgram createProgram(UUID actorId, String name, String description,
                                         UUID referrerRewardPromotionId, UUID referredRewardPromotionId,
                                         ReferralProgramStatus status, Integer maxReferralsPerReferrer) {
        requirePromotionExists(referrerRewardPromotionId);
        requirePromotionExists(referredRewardPromotionId);
        validateMaxReferralsPerReferrer(maxReferralsPerReferrer);

        ReferralProgramStatus effectiveStatus = status != null ? status : ReferralProgramStatus.ACTIVE;

        Instant now = Instant.now();
        ReferralProgram program = ReferralProgram.builder()
            .id(UUID.randomUUID())
            .name(name)
            .description(description)
            .referrerRewardPromotionId(referrerRewardPromotionId)
            .referredRewardPromotionId(referredRewardPromotionId)
            .status(effectiveStatus)
            .maxReferralsPerReferrer(maxReferralsPerReferrer)
            .createdAt(now).updatedAt(now)
            .createdBy(actorId).updatedBy(actorId)
            .build();

        ReferralProgram saved = programRepository.save(program);
        log.info("Referral program created id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    // ── updateProgram ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ReferralProgram updateProgram(UUID actorId, UUID id, String name, String description,
                                        UUID referrerRewardPromotionId, UUID referredRewardPromotionId,
                                        ReferralProgramStatus status, Integer maxReferralsPerReferrer) {
        ReferralProgram existing = programRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.REFERRAL_PROGRAM_NOT_FOUND, "Referral program not found: " + id));

        if (referrerRewardPromotionId != null) {
            requirePromotionExists(referrerRewardPromotionId);
        }
        if (referredRewardPromotionId != null) {
            requirePromotionExists(referredRewardPromotionId);
        }
        if (maxReferralsPerReferrer != null) {
            validateMaxReferralsPerReferrer(maxReferralsPerReferrer);
        }

        ReferralProgram updated = ReferralProgram.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .name(name != null ? name : existing.getName())
            .description(description != null ? description : existing.getDescription())
            .referrerRewardPromotionId(
                referrerRewardPromotionId != null ? referrerRewardPromotionId : existing.getReferrerRewardPromotionId())
            .referredRewardPromotionId(
                referredRewardPromotionId != null ? referredRewardPromotionId : existing.getReferredRewardPromotionId())
            .status(status != null ? status : existing.getStatus())
            .maxReferralsPerReferrer(
                maxReferralsPerReferrer != null ? maxReferralsPerReferrer : existing.getMaxReferralsPerReferrer())
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(actorId)
            .build();

        ReferralProgram saved = programRepository.save(updated);
        log.info("Referral program updated id={}", saved.getId());
        return saved;
    }

    // ── getProgram ────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public ReferralProgram getProgram(UUID id) {
        log.debug("referral_program.get id={}", id);
        return programRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.REFERRAL_PROGRAM_NOT_FOUND, "Referral program not found: " + id));
    }

    // ── listPrograms ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ReferralProgram> listPrograms() {
        log.debug("referral_program.list");
        return programRepository.findAll();
    }

    // ── deleteProgram ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteProgram(UUID actorId, UUID id) {
        programRepository.softDelete(id, actorId);
        log.info("Referral program soft-deleted id={}", id);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void requirePromotionExists(UUID promotionId) {
        promotionRepository.findById(promotionId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.PROMOTION_NOT_FOUND, "Promotion not found: " + promotionId));
    }

    private static void validateMaxReferralsPerReferrer(Integer maxReferralsPerReferrer) {
        if (maxReferralsPerReferrer != null && maxReferralsPerReferrer < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "maxReferralsPerReferrer must be at least 1 when set.");
        }
    }
}
