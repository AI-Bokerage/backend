package com.company.ppmsvc.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Canonical error codes for the PPM Service.
 *
 * <p>Each constant carries a machine-readable {@code code} string (safe to
 * expose in API responses) and a human-readable {@code defaultMessage}.
 *
 * <p>Code ranges:
 * <pre>
 *   PPM-0xxx  General / infrastructure errors
 *   PPM-1xxx  Business rule violations
 *   PPM-2xxx  Plan domain errors
 *   PPM-3xxx  Module domain errors
 *   PPM-4xxx  Entitlement / catalog errors
 *   PPM-9xxx  Security / access errors
 * </pre>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── General ───────────────────────────────────────────────────────────────
    INTERNAL_SERVER_ERROR   ("PPM-0001", "An unexpected error occurred."),
    VALIDATION_ERROR        ("PPM-0002", "One or more input fields are invalid."),
    RESOURCE_NOT_FOUND      ("PPM-0003", "The requested resource was not found."),
    DUPLICATE_RESOURCE      ("PPM-0004", "A resource with the same identity already exists."),
    OPTIMISTIC_LOCK_CONFLICT("PPM-0005", "The resource was modified concurrently. Please retry."),
    ILLEGAL_ARGUMENT        ("PPM-0006", "An illegal argument was provided."),

    // ── Business rules ────────────────────────────────────────────────────────
    BUSINESS_RULE_VIOLATION ("PPM-1001", "A business rule was violated."),
    INVALID_STATE_TRANSITION("PPM-1002", "The requested state transition is not allowed."),

    // ── Plan ──────────────────────────────────────────────────────────────────
    PLAN_NOT_FOUND              ("PPM-2001", "Plan not found."),
    PLAN_NOT_PUBLIC             ("PPM-2002", "Plan is not publicly available."),
    PLAN_CODE_ALREADY_EXISTS    ("PPM-2003", "A plan with the given code already exists."),
    PLAN_SLUG_ALREADY_EXISTS    ("PPM-2004", "A plan with the given slug already exists."),

    // ── Module ────────────────────────────────────────────────────────────────
    MODULE_NOT_FOUND              ("PPM-3001", "Module not found."),
    MODULE_NOT_INCLUDED_IN_PLAN   ("PPM-3002", "Module is not included in the requested plan."),
    MODULE_CODE_ALREADY_EXISTS    ("PPM-3003", "A module with the given code already exists."),
    MODULE_ALREADY_ASSIGNED_TO_PLAN("PPM-3004", "This module is already assigned to the plan."),
    PLAN_MODULE_MAPPING_NOT_FOUND ("PPM-3005", "No mapping found for the given plan and module."),

    // ── Entitlement / Catalog ─────────────────────────────────────────────────
    ENTITLEMENT_RESOLVE_FAILED          ("PPM-4001", "Failed to resolve entitlements for the given plan."),
    CATALOG_VERSION_MISMATCH            ("PPM-4002", "Catalog version does not match the expected version."),
    ENTITLEMENT_NOT_FOUND               ("PPM-4003", "Entitlement not found."),
    ENTITLEMENT_CODE_ALREADY_EXISTS     ("PPM-4004", "An entitlement with the given code already exists."),
    PLAN_ENTITLEMENT_MAPPING_NOT_FOUND  ("PPM-4005", "No entitlement mapping found for the given plan and entitlement."),
    PLAN_ENTITLEMENT_ALREADY_ASSIGNED   ("PPM-4006", "This entitlement is already assigned to the plan."),

    // ── Plan Price ────────────────────────────────────────────────────────────
    PLAN_PRICE_NOT_FOUND    ("PPM-5001", "Plan price not found."),
    PLAN_PRICE_ALREADY_EXISTS("PPM-5002", "A price for this plan, region, currency, cycle, and effective date already exists."),
    PLAN_PRICE_NOT_RESOLVED ("PPM-5003", "No active price exists for the requested plan, region, currency, and billing cycle."),

    // ── Plan Version ──────────────────────────────────────────────────────────
    PLAN_VERSION_NOT_FOUND      ("PPM-6001", "Plan version not found."),
    PLAN_VERSION_ALREADY_EXISTS ("PPM-6002", "A version with the given number or effective date already exists for this plan."),
    PLAN_VERSION_DATE_CONFLICT  ("PPM-6003", "The requested effective date falls within an existing version's active date range."),

    // ── Promo Code ────────────────────────────────────────────────────────────
    PROMO_CODE_NOT_FOUND             ("PPM-7001", "Promo code not found."),
    PROMO_CODE_ALREADY_EXISTS        ("PPM-7002", "A promo code with the given code already exists."),
    PROMO_CODE_PLAN_MAPPING_NOT_FOUND("PPM-7003", "No plan restriction mapping found for the given promo code and plan."),
    PROMO_CODE_PLAN_ALREADY_ASSIGNED  ("PPM-7004", "Promo code is already restricted to the given plan."),

    // ── Add-On Catalog ────────────────────────────────────────────────────────
    ADD_ON_NOT_FOUND              ("PPM-8001", "Add-on not found."),
    ADD_ON_CODE_ALREADY_EXISTS    ("PPM-8002", "An add-on with the given code already exists."),
    ADD_ON_PRICE_NOT_FOUND        ("PPM-8003", "Add-on price not found."),
    ADD_ON_PRICE_ALREADY_EXISTS   ("PPM-8004", "A price for this add-on, region, currency, cycle, and effective date already exists."),
    PLAN_ADD_ON_MAPPING_NOT_FOUND ("PPM-8005", "No add-on mapping found for the given plan and add-on."),
    PLAN_ADD_ON_ALREADY_ASSIGNED  ("PPM-8006", "This add-on is already assigned to the plan."),

    // ── Promotion ─────────────────────────────────────────────────────────────
    PROMOTION_NOT_FOUND        ("PPM-12001", "Promotion not found."),
    CONDITION_VALIDATION_ERROR ("PPM-12002", "Invalid condition configuration."),

    // ── Coupon ────────────────────────────────────────────────────────────────
    COUPON_NOT_FOUND           ("PPM-13001", "Coupon not found."),
    COUPON_CODE_ALREADY_EXISTS ("PPM-13002", "A coupon with the given code already exists."),

    // ── Referral ──────────────────────────────────────────────────────────────
    REFERRAL_PROGRAM_NOT_FOUND   ("PPM-14001", "Referral program not found."),
    REFERRAL_CODE_NOT_FOUND      ("PPM-14002", "Referral code not found."),
    REFERRAL_CODE_ALREADY_EXISTS ("PPM-14003", "A referral code with the given code already exists."),
    REFERRAL_EVENT_NOT_FOUND     ("PPM-14004", "Referral event not found."),
    REFERRAL_ALREADY_CONVERTED  ("PPM-14005", "This referral has already been converted for this customer."),
    REFERRAL_CAP_REACHED        ("PPM-14006", "The referrer has reached the maximum number of referrals for this program."),

    // ── Campaign ──────────────────────────────────────────────────────────────
    CAMPAIGN_NOT_FOUND        ("PPM-15001", "Campaign not found."),

    // ── Security ──────────────────────────────────────────────────────────────
    ACCESS_DENIED           ("PPM-9001", "You do not have permission to perform this action."),
    UNAUTHENTICATED         ("PPM-9002", "Authentication is required to access this resource.");

    private final String code;
    private final String defaultMessage;
}
