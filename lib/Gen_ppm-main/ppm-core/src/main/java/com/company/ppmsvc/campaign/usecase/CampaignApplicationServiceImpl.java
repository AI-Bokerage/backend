package com.company.ppmsvc.campaign.usecase;

import com.company.ppmsvc.campaign.model.Campaign;
import com.company.ppmsvc.campaign.model.CampaignStatus;
import com.company.ppmsvc.campaign.port.CampaignRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignApplicationServiceImpl implements CampaignApplicationService {

    private final CampaignRepositoryPort campaignRepository;

    // ── createCampaign ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Campaign createCampaign(UUID actorId, String name, String description,
                                   LocalDate validFrom, LocalDate validUntil, CampaignStatus status) {
        validateDateRange(validFrom, validUntil);

        CampaignStatus effectiveStatus = status != null ? status : CampaignStatus.DRAFT;

        Instant now = Instant.now();
        Campaign campaign = Campaign.builder()
            .id(UUID.randomUUID())
            .name(name)
            .description(description)
            .status(effectiveStatus)
            .validFrom(validFrom)
            .validUntil(validUntil)
            .createdAt(now).updatedAt(now)
            .createdBy(actorId).updatedBy(actorId)
            .build();

        Campaign saved = campaignRepository.save(campaign);
        log.info("Campaign created id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    // ── updateCampaign ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Campaign updateCampaign(UUID actorId, UUID id, String name, String description,
                                  LocalDate validFrom, LocalDate validUntil, CampaignStatus status) {
        Campaign existing = campaignRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("campaign.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.CAMPAIGN_NOT_FOUND, "Campaign not found: " + id);
            });

        LocalDate effectiveFrom  = validFrom  != null ? validFrom  : existing.getValidFrom();
        LocalDate effectiveUntil = validUntil != null ? validUntil : existing.getValidUntil();
        if (validFrom != null || validUntil != null) {
            validateDateRange(effectiveFrom, effectiveUntil);
        }

        Campaign updated = Campaign.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .name(name != null ? name : existing.getName())
            .description(description != null ? description : existing.getDescription())
            .status(status != null ? status : existing.getStatus())
            .validFrom(effectiveFrom)
            .validUntil(effectiveUntil)
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(actorId)
            .build();

        Campaign saved = campaignRepository.save(updated);
        log.info("Campaign updated id={}", saved.getId());
        return saved;
    }

    // ── getCampaign ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Campaign getCampaign(UUID id) {
        log.debug("campaign.get id={}", id);
        return campaignRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("campaign.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.CAMPAIGN_NOT_FOUND, "Campaign not found: " + id);
            });
    }

    // ── listCampaigns ─────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Campaign> listCampaigns(CampaignStatus statusFilter) {
        log.debug("campaign.list status={}", statusFilter);
        return campaignRepository.findAll().stream()
            .filter(c -> statusFilter == null || c.getStatus() == statusFilter)
            .toList();
    }

    // ── deleteCampaign ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteCampaign(UUID actorId, UUID id) {
        campaignRepository.softDelete(id, actorId);
        log.info("Campaign soft-deleted id={}", id);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void validateDateRange(LocalDate validFrom, LocalDate validUntil) {
        if (validUntil.isBefore(validFrom)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "validUntil must not be before validFrom.");
        }
    }
}
