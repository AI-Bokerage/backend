package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.campaign.port.CampaignRepositoryPort;
import com.company.ppmsvc.exception.BusinessException;
import com.company.ppmsvc.exception.ErrorCode;
import com.company.ppmsvc.exception.ResourceNotFoundException;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.promotion.model.EntitlementAction;
import com.company.ppmsvc.promotion.model.FixedPriceDiscount;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.FreeAddOnDiscount;
import com.company.ppmsvc.promotion.model.FreeModuleDiscount;
import com.company.ppmsvc.promotion.model.FreePeriodDiscount;
import com.company.ppmsvc.promotion.model.PercentageDiscount;
import com.company.ppmsvc.promotion.model.PlanRestrictionCondition;
import com.company.ppmsvc.promotion.model.PriceAction;
import com.company.ppmsvc.promotion.model.Promotion;
import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.company.ppmsvc.promotion.model.PromotionSource;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import com.company.ppmsvc.promotion.port.PromotionRepositoryPort;
import java.math.BigDecimal;
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
public class PromotionApplicationServiceImpl implements PromotionApplicationService {

    private final PromotionRepositoryPort promotionRepository;
    private final CampaignRepositoryPort   campaignRepository;
    private final ModuleRepositoryPort     moduleRepository;
    private final AddOnRepositoryPort      addOnRepository;

    // ── createPromotion ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Promotion createPromotion(UUID actorId, String name, String description, PromotionAction action,
                                      LocalDate validFrom, LocalDate validUntil, PromotionStatus status,
                                      PromotionSource source, UUID campaignId, List<PromotionCondition> conditions,
                                      Integer usageCapPerUser) {
        validateDateRange(validFrom, validUntil);
        validateAction(action);
        verifyEntitlementTargets(action);
        validateConditions(conditions);
        validateUsageCapPerUser(usageCapPerUser);
        if (campaignId != null) {
            requireCampaignExists(campaignId);
        }

        PromotionStatus effectiveStatus = status != null ? status : PromotionStatus.ACTIVE;

        Instant now = Instant.now();
        Promotion promotion = Promotion.builder()
            .id(UUID.randomUUID())
            .name(name)
            .description(description)
            .action(action)
            .validFrom(validFrom)
            .validUntil(validUntil)
            .status(effectiveStatus)
            .source(source)
            .campaignId(campaignId)
            .conditions(conditions != null ? conditions : List.of())
            .usageCapPerUser(usageCapPerUser)
            .createdAt(now).updatedAt(now)
            .createdBy(actorId).updatedBy(actorId)
            .build();

        Promotion saved = promotionRepository.save(promotion);
        log.info("Promotion created id={} name={}", saved.getId(), saved.getName());
        return saved;
    }

    // ── updatePromotion ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public Promotion updatePromotion(UUID actorId, UUID id, String name, String description, PromotionAction action,
                                      LocalDate validFrom, LocalDate validUntil, PromotionStatus status,
                                      PromotionSource source, UUID campaignId, List<PromotionCondition> conditions,
                                      Integer usageCapPerUser) {
        Promotion existing = promotionRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("promotion.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.PROMOTION_NOT_FOUND, "Promotion not found: " + id);
            });

        LocalDate effectiveFrom  = validFrom  != null ? validFrom  : existing.getValidFrom();
        LocalDate effectiveUntil = validUntil != null ? validUntil : existing.getValidUntil();
        if (validFrom != null || validUntil != null) {
            validateDateRange(effectiveFrom, effectiveUntil);
        }

        PromotionAction effectiveAction = action != null ? action : existing.getAction();
        if (action != null) {
            validateAction(effectiveAction);
            verifyEntitlementTargets(effectiveAction);
        }

        List<PromotionCondition> effectiveConditions = conditions != null ? conditions : existing.getConditions();
        if (conditions != null) {
            validateConditions(effectiveConditions);
        }

        Integer effectiveUsageCapPerUser = usageCapPerUser != null ? usageCapPerUser : existing.getUsageCapPerUser();
        if (usageCapPerUser != null) {
            validateUsageCapPerUser(effectiveUsageCapPerUser);
        }

        // campaignId is nullable domain state: null always means "no campaign"
        // (not "leave unchanged") — a non-null value is verified and applied.
        if (campaignId != null) {
            requireCampaignExists(campaignId);
        }

        Promotion updated = Promotion.builder()
            .id(existing.getId())
            .version(existing.getVersion())
            .name(name != null ? name : existing.getName())
            .description(description != null ? description : existing.getDescription())
            .action(effectiveAction)
            .validFrom(effectiveFrom)
            .validUntil(effectiveUntil)
            .status(status != null ? status : existing.getStatus())
            .source(source != null ? source : existing.getSource())
            .campaignId(campaignId)
            .conditions(effectiveConditions)
            .usageCapPerUser(effectiveUsageCapPerUser)
            .createdAt(existing.getCreatedAt())
            .createdBy(existing.getCreatedBy())
            .updatedAt(Instant.now())
            .updatedBy(actorId)
            .build();

        Promotion saved = promotionRepository.save(updated);
        log.info("Promotion updated id={}", saved.getId());
        return saved;
    }

    // ── getPromotion ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Promotion getPromotion(UUID id) {
        log.debug("promotion.get id={}", id);
        return promotionRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("promotion.not_found id={}", id);
                return new ResourceNotFoundException(
                    ErrorCode.PROMOTION_NOT_FOUND, "Promotion not found: " + id);
            });
    }

    // ── listPromotions ────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Promotion> listPromotions(PromotionStatus statusFilter) {
        log.debug("promotion.list status={}", statusFilter);
        return promotionRepository.findAll().stream()
            .filter(p -> statusFilter == null || p.getStatus() == statusFilter)
            .toList();
    }

    // ── deletePromotion ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deletePromotion(UUID actorId, UUID id) {
        promotionRepository.softDelete(id, actorId);
        log.info("Promotion soft-deleted id={}", id);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void requireCampaignExists(UUID campaignId) {
        campaignRepository.findById(campaignId)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.CAMPAIGN_NOT_FOUND, "Campaign not found: " + campaignId));
    }

    /** Verifies {@link FreeModuleDiscount}/{@link FreeAddOnDiscount} targets exist in the catalog. */
    private void verifyEntitlementTargets(PromotionAction action) {
        switch (action) {
            case PriceAction ignored -> { }
            case EntitlementAction ea -> {
                switch (ea) {
                    case FreePeriodDiscount ignored -> { }
                    case FreeModuleDiscount fm -> moduleRepository.findById(fm.moduleId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.MODULE_NOT_FOUND, "Module not found: " + fm.moduleId()));
                    case FreeAddOnDiscount fa -> addOnRepository.findById(fa.addOnId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.ADD_ON_NOT_FOUND, "Add-on not found: " + fa.addOnId()));
                }
            }
        }
    }

    private static void validateDateRange(LocalDate validFrom, LocalDate validUntil) {
        if (validUntil.isBefore(validFrom)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "validUntil must not be before validFrom.");
        }
    }

    private static void validateAction(PromotionAction action) {
        switch (action) {
            case PriceAction pa -> validatePriceAction(pa);
            case EntitlementAction ea -> validateEntitlementAction(ea);
        }
    }

    private static void validatePriceAction(PriceAction action) {
        switch (action) {
            case PercentageDiscount pct -> {
                if (pct.percentage().compareTo(BigDecimal.ZERO) <= 0
                        || pct.percentage().compareTo(new BigDecimal("100")) > 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Percentage must be greater than 0 and at most 100.");
                }
                if (pct.maxDiscountValue() != null && pct.maxDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "maxDiscountValue must be greater than zero when set.");
                }
                if (pct.minDiscountValue() != null && pct.minDiscountValue().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "minDiscountValue must be greater than zero when set.");
                }
                if (pct.maxDiscountValue() != null && pct.minDiscountValue() != null
                        && pct.maxDiscountValue().compareTo(pct.minDiscountValue()) < 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "maxDiscountValue must not be less than minDiscountValue.");
                }
            }
            case FlatDiscount flat -> {
                if (flat.amount().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Flat discount amount must be greater than zero.");
                }
            }
            case FixedPriceDiscount fp -> {
                if (fp.price().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "Fixed price must be greater than zero.");
                }
            }
        }
    }

    private static void validateEntitlementAction(EntitlementAction action) {
        Integer durationMonths = switch (action) {
            case FreePeriodDiscount fp -> fp.durationMonths();
            case FreeModuleDiscount fm -> fm.durationMonths();
            case FreeAddOnDiscount fa -> fa.durationMonths();
        };
        if (durationMonths == null || durationMonths < 1 || durationMonths > 12) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "durationMonths must be between 1 and 12.");
        }
    }

    private static void validateConditions(List<PromotionCondition> conditions) {
        if (conditions == null) {
            return;
        }
        for (PromotionCondition condition : conditions) {
            if (condition == null) {
                throw new BusinessException(ErrorCode.CONDITION_VALIDATION_ERROR,
                    "Condition entries must not be null.");
            }
            if (condition instanceof PlanRestrictionCondition prc && prc.planIds() == null) {
                throw new BusinessException(ErrorCode.CONDITION_VALIDATION_ERROR,
                    "PlanRestrictionCondition.planIds must not be null (use an empty set for unrestricted).");
            }
            if (condition instanceof com.company.ppmsvc.promotion.model.EligibilityCondition ec && ec.type() == null) {
                throw new BusinessException(ErrorCode.CONDITION_VALIDATION_ERROR,
                    "EligibilityCondition.type must not be null.");
            }
        }
    }

    private static void validateUsageCapPerUser(Integer usageCapPerUser) {
        if (usageCapPerUser != null && usageCapPerUser < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "usageCapPerUser must be at least 1 when set.");
        }
    }
}
