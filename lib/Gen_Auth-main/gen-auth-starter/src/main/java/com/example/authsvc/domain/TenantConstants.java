package com.example.authsvc.domain;

import java.util.UUID;

public final class TenantConstants {

    private TenantConstants() {}

    /** Sentinel tenant id for actors that aren't scoped to a real tenant (super-admins, single-tenant deployments). */
    public static final UUID PLATFORM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /** Stable sentinel subject for tokens minted by {@code ServiceTokenServiceImpl} — not tied to any real user or tenant. */
    public static final UUID SERVICE_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
}
