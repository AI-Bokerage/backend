package com.company.ppmsvc.referral.model;

import com.company.ppmsvc.common.AuditableEntity;
import com.company.ppmsvc.promotion.model.ReferralProgramStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * A referral program — pairs a referrer reward and a referred-customer
 * reward, each expressed as a {@code Promotion}.
 *
 * <p><strong>Architectural invariant:</strong> this aggregate references
 * Promotions by ID but never owns or embeds them. The reward Promotions are
 * created and managed via the existing Promotion catalog (Phase 0 CRUD);
 * {@code ReferralProgram} only holds their IDs. Do not embed Promotion state
 * here, and do not create Promotions inline from referral use-cases.
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class ReferralProgram extends AuditableEntity {

    private final String name;
    private final String description;
    private final UUID referrerRewardPromotionId;
    private final UUID referredRewardPromotionId;
    private final ReferralProgramStatus status;
    private final Integer maxReferralsPerReferrer;

    @Builder
    private ReferralProgram(UUID id, Long version,
                            Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                            String name, String description, UUID referrerRewardPromotionId,
                            UUID referredRewardPromotionId, ReferralProgramStatus status,
                            Integer maxReferralsPerReferrer) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version                   = version;
        this.name                      = name;
        this.description               = description;
        this.referrerRewardPromotionId = referrerRewardPromotionId;
        this.referredRewardPromotionId = referredRewardPromotionId;
        this.status                    = status;
        this.maxReferralsPerReferrer    = maxReferralsPerReferrer;
    }
}
