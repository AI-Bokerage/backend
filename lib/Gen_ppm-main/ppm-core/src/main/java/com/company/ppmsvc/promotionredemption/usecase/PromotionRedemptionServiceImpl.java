package com.company.ppmsvc.promotionredemption.usecase;

import com.company.ppmsvc.promotion.model.PromotionAction;
import com.company.ppmsvc.promotionredemption.model.PromotionRedemption;
import com.company.ppmsvc.promotionredemption.port.PromotionRedemptionRepositoryPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionRedemptionServiceImpl implements PromotionRedemptionService {

    private final PromotionRedemptionRepositoryPort redemptionRepository;

    @Override
    @Transactional
    public PromotionRedemption recordRedemption(UUID promotionId, String customerId, UUID planId,
                                                PromotionAction appliedAction, BigDecimal discountAmount) {
        Instant now = Instant.now();
        PromotionRedemption redemption = PromotionRedemption.builder()
            .id(UUID.randomUUID())
            .promotionId(promotionId)
            .customerId(customerId)
            .planId(planId)
            .redeemedAt(now)
            .appliedAction(appliedAction)
            .discountAmount(discountAmount)
            .createdAt(now).updatedAt(now)
            .build();

        PromotionRedemption saved = redemptionRepository.save(redemption);
        log.info("Promotion redeemed promotionId={} customerId={}", promotionId, customerId);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public int getUsageCount(UUID promotionId, String customerId) {
        log.debug("redemption.usage_count promotionId={} customerId={}", promotionId, customerId);
        return redemptionRepository.countByPromotionIdAndCustomerId(promotionId, customerId);
    }
}
