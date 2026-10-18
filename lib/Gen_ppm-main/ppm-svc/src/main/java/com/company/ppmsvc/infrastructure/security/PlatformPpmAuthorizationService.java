package com.company.ppmsvc.infrastructure.security;

import com.company.ppmsvc.exception.AccessDeniedException;
import com.company.ppmsvc.security.PpmAuthorizationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * This platform's authorization rules: reads are open to any authenticated
 * caller, writes and quotes require SUPER_ADMIN — preserving the behaviour
 * that previously lived in {@link PpmAdminAuthorizationFilter}.
 */
public class PlatformPpmAuthorizationService implements PpmAuthorizationService {

    @Override
    public void authorizePromotionRead() {
        // open to any authenticated caller
    }

    @Override
    public void authorizePromotionWrite() {
        requireSuperAdmin();
    }

    @Override
    public void authorizeCouponRead() {
        // open to any authenticated caller
    }

    @Override
    public void authorizeCouponWrite() {
        requireSuperAdmin();
    }

    @Override
    public void authorizeCampaignRead() {
        // open to any authenticated caller
    }

    @Override
    public void authorizeCampaignWrite() {
        requireSuperAdmin();
    }

    @Override
    public void authorizeReferralRead() {
        // open to any authenticated caller
    }

    @Override
    public void authorizeReferralWrite() {
        requireSuperAdmin();
    }

    @Override
    public void authorizeQuote() {
        requireSuperAdmin();
    }

    @Override
    public void authorizeAdminWrite() {
        requireSuperAdmin();
    }

    private void requireSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof CpmsAuthenticatedPrincipal principal
                && principal.isSuperAdmin())) {
            throw new AccessDeniedException("This endpoint requires Super Admin access");
        }
    }
}
