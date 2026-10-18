package com.example.authsvc.domain.enums;

/**
 * Represents all first-class user types that can authenticate in the CPMS platform.
 * Used in JWT claims and session metadata to drive authorization decisions.
 */
public enum UserType {

    /** Standard user belonging to a tenant organization. */
    TENANT_USER,

    /** External client (prospect or partner) with limited access. */
    CLIENT,

    /** Platform super-administrator with unrestricted access. */
    SUPER_ADMIN,

    /** Unverified user who has initiated registration but not completed it. */
    PROSPECT,

    /** Super-admin acting on behalf of a tenant (impersonation session). */
    SUPER_ADMIN_IMPERSONATING
}
