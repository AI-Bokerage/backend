package com.company.ppmsvc.api.dto.request;

import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotion.model.PromotionCondition;
import com.company.ppmsvc.promotion.model.PromotionSource;
import com.company.ppmsvc.promotion.model.PromotionStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for partially updating an existing promotion (PATCH semantics).
 * A {@code null} value means "leave unchanged" for every field <em>except</em>
 * {@code campaignId}, which is nullable domain state — omitting it (null)
 * always means "no campaign"; see {@code PromotionApplicationService} Javadoc.
 * A non-null {@code action} or {@code conditions} replaces the existing value
 * and is revalidated.
 */
public record UpdatePromotionRequest(

    String                    name,
    String                    description,
    PromotionAction           action,
    LocalDate                 validFrom,
    LocalDate                 validUntil,
    PromotionStatus           status,
    PromotionSource           source,
    UUID                      campaignId,
    List<PromotionCondition>  conditions,
    Integer                   usageCapPerUser
) {}
