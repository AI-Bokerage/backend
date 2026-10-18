package com.company.ppmsvc.unit.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.ppmsvc.exception.AccessDeniedException;
import com.company.ppmsvc.infrastructure.security.CpmsAuthenticatedPrincipal;
import com.company.ppmsvc.infrastructure.security.CpmsUserType;
import com.company.ppmsvc.infrastructure.security.PlatformPpmAuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PlatformPpmAuthorizationServiceTest {

    private final PlatformPpmAuthorizationService service = new PlatformPpmAuthorizationService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void setUpPrincipal(CpmsUserType userType) {
        UUID roleId = (userType == CpmsUserType.SUPER_ADMIN) ? null : UUID.randomUUID();
        CpmsAuthenticatedPrincipal principal = new CpmsAuthenticatedPrincipal(
            UUID.randomUUID(), UUID.randomUUID(), "slug", roleId,
            userType, "session", "jti-1", Instant.now().plusSeconds(900)
        );
        var auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void reads_areOpenToAnyAuthenticatedCaller() {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        assertThatCode(service::authorizePromotionRead).doesNotThrowAnyException();
        assertThatCode(service::authorizeCouponRead).doesNotThrowAnyException();
        assertThatCode(service::authorizeCampaignRead).doesNotThrowAnyException();
        assertThatCode(service::authorizeReferralRead).doesNotThrowAnyException();
    }

    @Test
    void superAdmin_writesAndQuote_permitted() {
        setUpPrincipal(CpmsUserType.SUPER_ADMIN);
        assertThatCode(service::authorizePromotionWrite).doesNotThrowAnyException();
        assertThatCode(service::authorizeCouponWrite).doesNotThrowAnyException();
        assertThatCode(service::authorizeCampaignWrite).doesNotThrowAnyException();
        assertThatCode(service::authorizeReferralWrite).doesNotThrowAnyException();
        assertThatCode(service::authorizeQuote).doesNotThrowAnyException();
        assertThatCode(service::authorizeAdminWrite).doesNotThrowAnyException();
    }

    @Test
    void tenantUser_writesAndQuote_denied() {
        setUpPrincipal(CpmsUserType.TENANT_USER);
        assertDenied(service::authorizePromotionWrite);
        assertDenied(service::authorizeCouponWrite);
        assertDenied(service::authorizeCampaignWrite);
        assertDenied(service::authorizeReferralWrite);
        assertDenied(service::authorizeQuote);
        assertDenied(service::authorizeAdminWrite);
    }

    @Test
    void noPrincipal_writeDenied() {
        assertDenied(service::authorizeAdminWrite);
    }

    private void assertDenied(Executable executable) {
        assertThatThrownBy(executable::execute).isInstanceOf(AccessDeniedException.class);
    }
}
