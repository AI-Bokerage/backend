package com.company.ppmsvc.referral.port;

import com.company.ppmsvc.referral.model.ReferralCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Domain port for {@link ReferralCode} persistence. No JPA types cross this interface. */
public interface ReferralCodeRepositoryPort {

    ReferralCode save(ReferralCode code);

    Optional<ReferralCode> findById(UUID id);

    List<ReferralCode> findAll();

    Optional<ReferralCode> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByProgramAndReferrer(UUID referralProgramId, String referrerCustomerId);

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_CODE_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
