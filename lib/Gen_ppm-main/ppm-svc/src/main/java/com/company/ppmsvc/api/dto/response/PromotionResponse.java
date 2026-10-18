package com.company.ppmsvc.api.dto.response;

import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.company.ppmsvc.promotion.model.PromotionSource;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * API response DTO representing a single promotion entry.
 *
 * <p>The following fields are intentionally absent to avoid leaking internal
 * state: {@code version}, {@code deletedAt}, {@code createdBy}, {@code updatedBy}.
 */
public record PromotionResponse(

    UUID                      id,
    String                    name,
    String                    description,
    PromotionAction           action,
    LocalDate                 validFrom,
    LocalDate                 validUntil,
    PromotionStatus           status,
    PromotionSource           source,
    UUID                      campaignId,
    List<PromotionCondition>  conditions,
    Integer                   usageCapPerUser,
    Instant                   createdAt,
    Instant                   updatedAt
) {}
