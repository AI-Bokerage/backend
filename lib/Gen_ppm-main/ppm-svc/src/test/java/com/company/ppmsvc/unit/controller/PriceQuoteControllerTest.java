package com.company.ppmsvc.unit.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.company.ppmsvc.api.controller.PriceQuoteController;
import com.company.ppmsvc.api.dto.request.PriceQuoteRequest;
import com.company.ppmsvc.common.BillingCycle;
import com.company.ppmsvc.exception.AccessDeniedException;
import com.company.ppmsvc.promotion.model.PriceQuote;
import com.company.ppmsvc.promotion.model.PromotionApplicationReason;
import com.company.ppmsvc.promotion.usecase.PromotionPricingService;
import com.company.ppmsvc.security.PpmAuthorizationService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PriceQuoteControllerTest {

    private final PromotionPricingService pricingService = mock(PromotionPricingService.class);
    private final PpmAuthorizationService authorizationService = mock(PpmAuthorizationService.class);
    private final PriceQuoteController controller =
        new PriceQuoteController(pricingService, authorizationService);

    private static final PriceQuoteRequest REQUEST =
        new PriceQuoteRequest(UUID.randomUUID(), "US", "USD", BillingCycle.MONTHLY, null, null);

    @Test
    void quote_delegatesToAuthorizeQuote_beforePricing() {
        when(pricingService.quote(any(), any(), any(), any(), any())).thenReturn(
            new PriceQuote(BigDecimal.TEN, "USD", null, BigDecimal.TEN,
                PromotionApplicationReason.NO_COUPON, null, null, true, null));

        controller.quote(REQUEST);

        verify(authorizationService).authorizeQuote();
    }

    @Test
    void quote_deniedByAuthorizationService_neverReachesPricing() {
        doThrow(new AccessDeniedException()).when(authorizationService).authorizeQuote();

        assertThatThrownBy(() -> controller.quote(REQUEST))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(pricingService);
    }
}
