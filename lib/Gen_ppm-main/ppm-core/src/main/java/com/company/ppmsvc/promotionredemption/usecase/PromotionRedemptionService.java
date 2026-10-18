package com.company.ppmsvc.promotionredemption.usecase;

import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotionredemption.model.PromotionRedemption;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Application service for the per-user promotion redemption ledger.
 */
public interface PromotionRedemptionService {

    /** Records a redemption. Append-only — always creates a new row. */
    PromotionRedemption recordRedemption(UUID promotionId, String customerId, UUID planId,
                                         PromotionAction appliedAction, BigDecimal discountAmount);

    /** Returns how many times {@code customerId} has redeemed {@code promotionId}. */
    int getUsageCount(UUID promotionId, String customerId);
}
