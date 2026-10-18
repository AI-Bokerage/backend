package com.company.ppmsvc.security;

import com.company.ppmsvc.exception.AccessDeniedException;

/**
 * Service Provider Interface for promotion-engine authorization decisions.
 *
 * <p>The library defines <em>what</em> authorization checks it needs; the
 * consuming application decides <em>how</em> they are enforced (platform
 * roles, JWT claims, OAuth scopes, or anything else). Implementations must
 * stay framework-independent — no Spring Security, Servlet API, or JWT types
 * may appear here.
 *
 * <p>Each method should throw {@link AccessDeniedException} when the current
 * caller is not permitted to proceed, and return normally otherwise.
 */
public interface PpmAuthorizationService {

    void authorizePromotionRead();

    void authorizePromotionWrite();

    void authorizeCouponRead();

    void authorizeCouponWrite();

    void authorizeCampaignRead();

    void authorizeCampaignWrite();

    void authorizeReferralRead();

    void authorizeReferralWrite();

    void authorizeQuote();

    /**
     * Gate for the platform-wide catalog write surface (plans, modules,
     * add-ons, entitlements, promo-codes, and the resources above) that isn't
     * yet broken out into its own capability.
     */
    void authorizeAdminWrite();
}
