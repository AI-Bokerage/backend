package com.company.ppmsvc.campaign.port;

import com.company.ppmsvc.campaign.model.Campaign;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Domain port for {@link Campaign} persistence. No JPA types cross this interface. */
public interface CampaignRepositoryPort {

    Campaign save(Campaign campaign);

    Optional<Campaign> findById(UUID id);

    List<Campaign> findAll();

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code CAMPAIGN_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
