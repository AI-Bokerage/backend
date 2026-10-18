package com.company.ppmsvc.referral.port;

import com.company.ppmsvc.referral.model.ReferralEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Domain port for {@link ReferralEvent} persistence. No JPA types cross this interface. */
public interface ReferralEventRepositoryPort {

    ReferralEvent save(ReferralEvent event);

    Optional<ReferralEvent> findById(UUID id);

    Optional<ReferralEvent> findByCodeAndCustomer(UUID referralCodeId, String referredCustomerId);

    /** Counts events with status {@code CONVERTED} for the given code — the referrer's cap usage. */
    int countConvertedByCode(UUID referralCodeId);

    List<ReferralEvent> findAll();

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code REFERRAL_EVENT_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
