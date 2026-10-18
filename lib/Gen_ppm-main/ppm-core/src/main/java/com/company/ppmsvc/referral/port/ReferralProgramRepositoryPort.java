package com.company.ppmsvc.referral.port;

import com.company.ppmsvc.referral.model.ReferralProgram;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Domain port for {@link ReferralProgram} persistence. No JPA types cross this interface. */
public interface ReferralProgramRepositoryPort {

    ReferralProgram save(ReferralProgram program);

    Optional<ReferralProgram> findById(UUID id);

    List<ReferralProgram> findAll();

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_PROGRAM_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
