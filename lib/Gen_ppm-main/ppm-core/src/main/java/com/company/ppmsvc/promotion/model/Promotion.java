package com.company.ppmsvc.promotion.model;

import com.company.ppmsvc.common.AuditableEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * A promotional offer — metadata plus a single {@link PromotionAction}, a
 * validity window, and a condition list.
 *
 * <p>A {@code Promotion} is the <em>offer</em>; a {@code Coupon} (see {@link
 * com.company.ppmsvc.coupon.model.Coupon}) is a redemption code that points
 * to one. Many coupons may point to the same promotion.
 *
 * <p>Phase 1 added {@link #conditions} (evaluated between status validation
 * and action application in the pipeline — see {@code
 * PromotionPricingServiceImpl}) and {@link #usageCapPerUser}, enforced
 * together with the {@code promotionredemption} ledger.
 *
 * <p>Phase 2 added {@link #source} — a distribution-channel tag, persisted
 * and never {@code null} (defaults to {@link PromotionSource#NORMAL}).
 *
 * <p>Phase 3 added {@link #campaignId} — an optional M:1 reference to a
 * {@code Campaign}. {@code null} means the promotion belongs to no campaign.
 * The campaign never gates this promotion's own evaluation — see {@code
 * Campaign}'s Javadoc for the invariant.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class Promotion extends AuditableEntity {

    private final String name;
    private final String description;
    private final PromotionAction action;
    private final LocalDate validFrom;
    private final LocalDate validUntil;
    private final PromotionStatus status;
    private final List<PromotionCondition> conditions;
    private final Integer usageCapPerUser;
    private final PromotionSource source;
    private final UUID campaignId;

    @Builder
    private Promotion(UUID id, Long version,
                      Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                      String name, String description, PromotionAction action,
                      LocalDate validFrom, LocalDate validUntil, PromotionStatus status,
                      List<PromotionCondition> conditions, Integer usageCapPerUser,
                      PromotionSource source, UUID campaignId) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version         = version;
        this.name            = name;
        this.description     = description;
        this.action          = action;
        this.validFrom       = validFrom;
        this.validUntil      = validUntil;
        this.status          = status;
        this.conditions      = conditions != null ? conditions : List.of();
        this.usageCapPerUser = usageCapPerUser;
        this.source          = source != null ? source : PromotionSource.NORMAL;
        this.campaignId      = campaignId;
    }
}
