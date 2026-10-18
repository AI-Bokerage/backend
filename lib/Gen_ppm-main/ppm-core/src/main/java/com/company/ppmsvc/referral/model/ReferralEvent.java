package com.company.ppmsvc.referral.model;

import com.company.ppmsvc.common.AuditableEntity;
import com.company.ppmsvc.promotion.model.ReferralEventStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

/**
 * Tracks one referral conversion attempt for a (code, referred customer)
 * pair — the deduplication mechanism for referrer rewards.
 *
 * <p>{@code rewardGrantedAt} marks when the referrer's reward was earned —
 * the durable grant fact, decoupled from however it is delivered (currently
 * a one-time {@code Coupon}; see {@code ReferralConversionServiceImpl}).
 *
 * <p><strong>Framework independence:</strong> no JPA, Spring, or other
 * infrastructure annotations appear in this class.
 */
@Getter
public class ReferralEvent extends AuditableEntity {

    private final UUID referralCodeId;
    private final String referredCustomerId;
    private final ReferralEventStatus status;
    private final Instant convertedAt;
    private final Instant rewardGrantedAt;

    @Builder
    private ReferralEvent(UUID id, Long version,
                          Instant createdAt, Instant updatedAt, UUID createdBy, UUID updatedBy,
                          UUID referralCodeId, String referredCustomerId, ReferralEventStatus status,
                          Instant convertedAt, Instant rewardGrantedAt) {
        super(id, createdAt, updatedAt, createdBy, updatedBy);
        this.version            = version;
        this.referralCodeId     = referralCodeId;
        this.referredCustomerId = referredCustomerId;
        this.status             = status;
        this.convertedAt        = convertedAt;
        this.rewardGrantedAt    = rewardGrantedAt;
    }
}
