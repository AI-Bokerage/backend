package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import com.company.ppmsvc.referral.model.ReferralProgram;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Referral Program catalog.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface ReferralProgramApplicationService {

    /**
     * Creates a new referral program.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Both {@code referrerRewardPromotionId} and {@code referredRewardPromotionId}
     *       must resolve to an existing {@code Promotion}.</li>
     *   <li>{@code status} defaults to {@link ReferralProgramStatus#ACTIVE} when null.</li>
     *   <li>{@code maxReferralsPerReferrer} must be ≥ 1 when set; {@code null} means unlimited.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMOTION_NOT_FOUND} if either reward promotion does not exist.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} if {@code maxReferralsPerReferrer} is invalid.
     */
    ReferralProgram createProgram(UUID actorId, String name, String description,
                                  UUID referrerRewardPromotionId, UUID referredRewardPromotionId,
                                  ReferralProgramStatus status, Integer maxReferralsPerReferrer);

    /**
     * Partially updates an existing referral program (PATCH semantics). A {@code null}
     * argument means "leave unchanged".
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_PROGRAM_NOT_FOUND} if the program does not exist, or
     *         {@code PROMOTION_NOT_FOUND} if a replaced reward promotion ID does not exist.
     */
    ReferralProgram updateProgram(UUID actorId, UUID id, String name, String description,
                                 UUID referrerRewardPromotionId, UUID referredRewardPromotionId,
                                 ReferralProgramStatus status, Integer maxReferralsPerReferrer);

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_PROGRAM_NOT_FOUND} if the program does not exist or is soft-deleted.
     */
    ReferralProgram getProgram(UUID id);

    List<ReferralProgram> listPrograms();

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_PROGRAM_NOT_FOUND} if the program does not exist or is already deleted.
     */
    void deleteProgram(UUID actorId, UUID id);
}
