package com.example.authsvc.config.redis;

/**
 * Centralised Redis key prefix constants for the auth service.
 *
 * <p>Key format convention: {@code auth:<domain>:<kind>:<discriminator>}
 * <ul>
 *   <li>{@link #LOCKOUT_FAILURES_PREFIX}  — per-identity failure counter (LockoutServiceImpl)</li>
 *   <li>{@link #LOCKOUT_LOCKED_PREFIX}    — per-identity lockout flag     (LockoutServiceImpl)</li>
 *   <li>{@link #LOCKOUT_COUNTER_PREFIX}   — failure counter               (RedisLockoutStore)</li>
 *   <li>{@link #LOCKOUT_LOCK_PREFIX}      — active lock flag              (RedisLockoutStore)</li>
 * </ul>
 */
public final class RedisKeyConfig {

    private RedisKeyConfig() {}

    // ─── Lockout ─────────────────────────────────────────────────────────────

    /** Failure count keyed by {@code email:ip}. TTL = lockout window. */
    public static final String LOCKOUT_FAILURES_PREFIX = "auth:lockout:failures:";

    /** Lockout flag keyed by {@code email:ip}. TTL = lock duration. */
    public static final String LOCKOUT_LOCKED_PREFIX   = "auth:lockout:locked:";

    /** Failure counter keyed by userId. TTL = lockout window. */
    public static final String LOCKOUT_COUNTER_PREFIX  = "auth:lockout:counter:";

    /** Active lock flag keyed by userId. TTL = lock duration. */
    public static final String LOCKOUT_LOCK_PREFIX     = "auth:lockout:lock:";
}
