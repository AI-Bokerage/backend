package com.example.authsvc.domain.enums;

/**
 * Discriminates between the different token artifacts issued by AUTH-SVC.
 * Each type carries a distinct TTL, signing key scope, and usage contract.
 */
public enum TokenType {

    /** Short-lived RS256 JWT carried in an HttpOnly cookie. TTL = 15 min. */
    ACCESS,

    /** Long-lived opaque token stored hashed in the DB. TTL = 7 days. */
    REFRESH,

    /** Single-use token embedded in a magic-link email URL. TTL = 24 h. */
    MAGIC_LINK,

    /** Scoped token issued when a super-admin impersonates a tenant user. TTL = 60 min. */
    IMPERSONATION
}
