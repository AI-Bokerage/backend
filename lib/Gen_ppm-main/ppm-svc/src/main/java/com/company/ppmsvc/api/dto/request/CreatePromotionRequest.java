package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.company.ppmsvc.promotion.model.PromotionSource;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a new promotion.
 *
 * <p>{@code action}'s concrete type is resolved via its {@code "type"} field
 * (see {@link PromotionAction}), e.g.
 * {@code {"type":"percentage","percentage":"20","maxDiscountValue":"200"}}.
 *
 * <p>{@code conditions} defaults to an empty list when omitted. {@code
 * usageCapPerUser} is optional — {@code null} means unlimited redemptions
 * per customer. {@code source} defaults to {@code normal} when omitted.
 * {@code campaignId} is optional — when set, the campaign must exist.
 */
public record CreatePromotionRequest(

    @NotBlank
    String name,

    String description,

    @NotNull @Valid
    PromotionAction action,

    @NotNull
    LocalDate validFrom,

    @NotNull
    LocalDate validUntil,

    PromotionStatus status,

    PromotionSource source,

    UUID campaignId,

    List<PromotionCondition> conditions,

    Integer usageCapPerUser
) {}
