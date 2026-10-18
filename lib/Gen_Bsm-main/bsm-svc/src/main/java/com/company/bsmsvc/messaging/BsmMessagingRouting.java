package com.company.bsmsvc.messaging;

/**
 * BSM-SVC's own outbound routing keys and target exchange. Owned here, not in a shared platform
 * library, because BSM is the sole producer of these events — a reusable library has no business
 * holding one service's private wire vocabulary alongside another service's.
 *
 * <p>{@code EVENTS_EXCHANGE} value ("cpms.events") is unchanged from the prior shared declaration
 * — only the Java ownership moved, not the broker-level contract, so existing consumers on this
 * exchange are unaffected.
 */
public final class BsmMessagingRouting {

    private BsmMessagingRouting() {}

    public static final String EVENTS_EXCHANGE = "cpms.events";

    public static final String BSM_SUBSCRIPTION_CREATED  = "bsm.subscription.created";
    public static final String BSM_SUBSCRIPTION_CHANGED  = "bsm.subscription.changed";
    public static final String BSM_SUBSCRIPTION_CANCELED = "bsm.subscription.canceled";
    public static final String BSM_SUBSCRIPTION_EXPIRED  = "bsm.subscription.expired";
    public static final String BSM_SUBSCRIPTION_RENEWED  = "bsm.subscription.renewed";

    public static final String BSM_ADDON_ACTIVATED   = "bsm.addon.activated";
    public static final String BSM_ADDON_DEACTIVATED = "bsm.addon.deactivated";
}
