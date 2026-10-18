package com.example.authsvc.infrastructure.messaging.constant;

public final class AuthExchangeConstants {

    private AuthExchangeConstants() {}

    // ─── Routing keys ────────────────────────────────────────────────────────
    public static final String RK_LOGIN_SUCCESS         = "auth.login.success";
    public static final String RK_LOGIN_FAILED          = "auth.login.failed";
    public static final String RK_LOGOUT                = "auth.logout";
    public static final String RK_PASSWORD_CHANGED      = "auth.password.changed";
    public static final String RK_IMPERSONATION_STARTED = "auth.impersonation.started";

    // ─── Audit-tier routing keys ────────────────────────────────────────────
    // RK_AUDIT_LOGOUT/RK_AUDIT_PASSWORD_CHANGED deliberately share string values
    // with RK_LOGOUT/RK_PASSWORD_CHANGED above — matches CPMS's real routing-key
    // reuse: the audit-tier row and the business row for the same logical event
    // share a routing key but go to different exchanges with different payload
    // shapes. Do not deduplicate these constants into one.
    public static final String RK_AUDIT_TENANT_LOGIN_SUCCESS   = "auth.tenant.login.success";
    public static final String RK_AUDIT_TENANT_LOGIN_FAILED    = "auth.tenant.login.failed";
    public static final String RK_AUDIT_PLATFORM_LOGIN_SUCCESS = "auth.superadmin.login.success";
    public static final String RK_AUDIT_PLATFORM_LOGIN_FAILED  = "auth.superadmin.login.failed";
    public static final String RK_AUDIT_LOGOUT                 = "auth.logout";
    public static final String RK_AUDIT_PASSWORD_CHANGED       = "auth.password.changed";
}
