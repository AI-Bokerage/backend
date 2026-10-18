package com.company.ppmsvc.campaign.usecase;

import com.company.ppmsvc.campaign.model.Campaign;
import com.company.ppmsvc.campaign.model.CampaignStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Campaign catalog.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface CampaignApplicationService {

    /**
     * Creates a new campaign.
     *
     * <p>Business rules:
     * <ul>
     *   <li>Date range: {@code validUntil} must not be before {@code validFrom}.</li>
     *   <li>{@code status} defaults to {@link CampaignStatus#DRAFT} when null — campaigns
     *       are planning objects, unlike promotions which default to ACTIVE.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} if the date range is invalid.
     */
    Campaign createCampaign(UUID actorId, String name, String description,
                            LocalDate validFrom, LocalDate validUntil, CampaignStatus status);

    /**
     * Partially updates an existing campaign (PATCH semantics). A {@code null}
     * argument means "leave unchanged".
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code CAMPAIGN_NOT_FOUND} if the campaign does not exist.
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code VALIDATION_ERROR} if the resulting date range is invalid.
     */
    Campaign updateCampaign(UUID actorId, UUID id, String name, String description,
                           LocalDate validFrom, LocalDate validUntil, CampaignStatus status);

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code CAMPAIGN_NOT_FOUND} if the campaign does not exist or is soft-deleted.
     */
    Campaign getCampaign(UUID id);

    /** Returns all non-deleted campaigns, optionally filtered by status. A {@code null} filter means "no filter". */
    List<Campaign> listCampaigns(CampaignStatus statusFilter);

    /**
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code CAMPAIGN_NOT_FOUND} if the campaign does not exist or is already deleted.
     */
    void deleteCampaign(UUID actorId, UUID id);
}
