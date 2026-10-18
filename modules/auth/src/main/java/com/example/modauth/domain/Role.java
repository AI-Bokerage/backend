package com.example.modauth.domain;

/**
 * Named role in the "Who uses the platform" hierarchy: Super Admin runs the
 * whole platform, Tenant Admin owns a brokerage, Team Lead runs a team of
 * brokers, Broker does the mortgage work. Drives the post-login dashboard
 * hint and who is allowed to invite whom.
 */
public enum Role {
    SUPER_ADMIN,
    TENANT_ADMIN,
    TEAM_LEAD,
    BROKER,
    // SOW Module 2.5 — supports a team like a Broker does, but with no own
    // book of leads to originate; shares the Broker dashboard shape.
    ASSISTANT;

    /** Which "Which role?" branch of the login/invitation-accept diagrams this maps to. */
    public String dashboardKey() {
        return switch (this) {
            case SUPER_ADMIN -> "platform_console";
            case TENANT_ADMIN -> "brokerage_dashboard";
            case TEAM_LEAD -> "team_dashboard";
            case BROKER, ASSISTANT -> "broker_dashboard";
        };
    }
}
