package com.company.ppmsvc.promotionredemption.port;

import com.company.ppmsvc.promotionredemption.model.PromotionRedemption;
import java.util.UUID;

/**
 * Domain port for {@link PromotionRedemption} persistence. No JPA types
 * cross this interface. Append-only — no update or delete.
 */
public interface PromotionRedemptionRepositoryPort {

    /** Persists a new redemption record and returns the saved state. */
    PromotionRedemption save(PromotionRedemption redemption);

    /** Returns how many times {@code customerId} has redeemed {@code promotionId}. */
    int countByPromotionIdAndCustomerId(UUID promotionId, String customerId);

    /** Convenience: {@code countByPromotionIdAndCustomerId(...) > 0}. */
    default boolean hasRedeemed(UUID promotionId, String customerId) {
        return countByPromotionIdAndCustomerId(promotionId, customerId) > 0;
    }
}
