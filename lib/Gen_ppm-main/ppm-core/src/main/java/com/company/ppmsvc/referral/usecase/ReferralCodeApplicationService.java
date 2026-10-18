package com.company.ppmsvc.referral.usecase;

import com.company.ppmsvc.promotion.model.ReferralCodeStatus;
import com.company.ppmsvc.referral.model.ReferralCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service for the Referral Code catalog.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface ReferralCodeApplicationService {

    /**
     * Creates a new referral code.
     *
     * <p>Business rules:
     * <ul>
     *   <li>{@code code} is normalised to uppercase with whitespace stripped, then
     *       checked for uniqueness.</li>
     *   <li>The referenced program must exist.</li>
     *   <li>One code per (program, referrer) pair.</li>
     *   <li>{@code status} defaults to {@link ReferralCodeStatus#ACTIVE} when null.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_PROGRAM_NOT_FOUND} if the program does not exist.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code REFERRAL_CODE_ALREADY_EXISTS} if the normalised code already exists, or
     *         {@code ILLEGAL_ARGUMENT} if this referrer already has a code for this program.
     */
    ReferralCode createCode(UUID actorId, String code, UUID referralProgramId, String referrerCustomerId,
                            ReferralCodeStatus status);

    /**
     * Partially updates an existing referral code (PATCH semantics). {@code code} is
     * immutable after creation and has no parameter here.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_CODE_NOT_FOUND} if the code does not exist.
     */
    ReferralCode updateCode(UUID actorId, UUID id, ReferralCodeStatus status);

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_CODE_NOT_FOUND} if the code does not exist or is soft-deleted.
     */
    ReferralCode getCode(UUID id);

    Optional<ReferralCode> getCodeByCode(String code);

    List<ReferralCode> listCodes();

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_CODE_NOT_FOUND} if the code does not exist or is already deleted.
     */
    void deleteCode(UUID actorId, UUID id);
}
