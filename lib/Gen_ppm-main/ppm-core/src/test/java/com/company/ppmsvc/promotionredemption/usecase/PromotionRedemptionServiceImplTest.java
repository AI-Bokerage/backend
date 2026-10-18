package com.company.ppmsvc.promotionredemption.usecase;

import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotionredemption.model.PromotionRedemption;
import com.company.ppmsvc.promotionredemption.port.PromotionRedemptionRepositoryPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionRedemptionServiceImpl")
class PromotionRedemptionServiceImplTest {

    static final UUID PROMOTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    static final UUID PLAN_ID      = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock PromotionRedemptionRepositoryPort redemptionRepository;

    @InjectMocks PromotionRedemptionServiceImpl service;

    @Test
    @DisplayName("recordRedemption() saves a new redemption with the given fields")
    void recordRedemption_savesCorrectly() {
        FlatDiscount action = new FlatDiscount(new BigDecimal("50"));
        when(redemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<PromotionRedemption> captor = ArgumentCaptor.forClass(PromotionRedemption.class);
        service.recordRedemption(PROMOTION_ID, "cust-1", PLAN_ID, action, new BigDecimal("50.0000"));

        verify(redemptionRepository).save(captor.capture());
        PromotionRedemption saved = captor.getValue();
        assertThat(saved.getPromotionId()).isEqualTo(PROMOTION_ID);
        assertThat(saved.getCustomerId()).isEqualTo("cust-1");
        assertThat(saved.getPlanId()).isEqualTo(PLAN_ID);
        assertThat(saved.getAppliedAction()).isEqualTo(action);
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("50.0000");
    }

    @Test
    @DisplayName("getUsageCount() delegates to the repository")
    void getUsageCount_delegatesToRepository() {
        when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1")).thenReturn(3);

        int count = service.getUsageCount(PROMOTION_ID, "cust-1");

        assertThat(count).isEqualTo(3);
        verify(redemptionRepository).countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-1");
    }

    @Test
    @DisplayName("0 redemptions — returns count 0")
    void getUsageCount_zeroRedemptions_returnsZero() {
        when(redemptionRepository.countByPromotionIdAndCustomerId(PROMOTION_ID, "cust-2")).thenReturn(0);

        assertThat(service.getUsageCount(PROMOTION_ID, "cust-2")).isZero();
    }
}
