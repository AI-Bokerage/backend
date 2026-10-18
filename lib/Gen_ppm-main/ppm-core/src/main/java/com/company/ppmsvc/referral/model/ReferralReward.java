package com.company.ppmsvc.referral.model;

import java.util.UUID;

/**
 * The result of a reward grant.
 *
 * <p>{@code couponCode} is populated under the current coupon-delivery
 * mechanism; under a future mechanism (wallet credit, etc.) it would carry a
 * different identifier or be absent — the return-type contract does not
 * change. The durable grant fact lives in {@link ReferralEvent#getRewardGrantedAt()},
 * not here — this record is just the computed result handed back to the caller.
 */
public record ReferralReward(UUID promotionId, String customerId, String couponCode) {
}
