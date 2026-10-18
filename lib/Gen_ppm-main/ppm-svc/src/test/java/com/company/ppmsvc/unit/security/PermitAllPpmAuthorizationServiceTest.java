package com.company.ppmsvc.unit.security;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.company.ppmsvc.infrastructure.security.PermitAllPpmAuthorizationService;
import org.junit.jupiter.api.Test;

class PermitAllPpmAuthorizationServiceTest {

    private final PermitAllPpmAuthorizationService service = new PermitAllPpmAuthorizationService();

    @Test
    void permitsEveryOperation() {
        assertThatCode(service::authorizePromotionRead).doesNotThrowAnyException();
        assertThatCode(service::authorizePromotionWrite).doesNotThrowAnyException();
        assertThatCode(service::authorizeCouponRead).doesNotThrowAnyException();
        assertThatCode(service::authorizeCouponWrite).doesNotThrowAnyException();
        assertThatCode(service::authorizeCampaignRead).doesNotThrowAnyException();
        assertThatCode(service::authorizeCampaignWrite).doesNotThrowAnyException();
        assertThatCode(service::authorizeReferralRead).doesNotThrowAnyException();
        assertThatCode(service::authorizeReferralWrite).doesNotThrowAnyException();
        assertThatCode(service::authorizeQuote).doesNotThrowAnyException();
        assertThatCode(service::authorizeAdminWrite).doesNotThrowAnyException();
    }
}
