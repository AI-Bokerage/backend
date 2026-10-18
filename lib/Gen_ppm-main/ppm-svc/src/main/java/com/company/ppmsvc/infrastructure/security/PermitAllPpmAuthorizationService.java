package com.company.ppmsvc.infrastructure.security;

import com.company.ppmsvc.security.PpmAuthorizationService;

/**
 * Library default: permits every operation. Used when the consuming
 * application does not define its own {@link PpmAuthorizationService} bean.
 */
public class PermitAllPpmAuthorizationService implements PpmAuthorizationService {

    @Override
    public void authorizePromotionRead() {
        // intentionally empty
    }

    @Override
    public void authorizePromotionWrite() {
        // intentionally empty
    }

    @Override
    public void authorizeCouponRead() {
        // intentionally empty
    }

    @Override
    public void authorizeCouponWrite() {
        // intentionally empty
    }

    @Override
    public void authorizeCampaignRead() {
        // intentionally empty
    }

    @Override
    public void authorizeCampaignWrite() {
        // intentionally empty
    }

    @Override
    public void authorizeReferralRead() {
        // intentionally empty
    }

    @Override
    public void authorizeReferralWrite() {
        // intentionally empty
    }

    @Override
    public void authorizeQuote() {
        // intentionally empty
    }

    @Override
    public void authorizeAdminWrite() {
        // intentionally empty
    }
}
