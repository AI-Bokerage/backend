package com.example.admsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * No permission codes are hardcoded anywhere in Gen_ADM — the consuming
 * app supplies its own catalog here, and {@link com.example.admsvc.infrastructure.startup.PermissionCatalogInitializer}
 * upserts it into the {@code permissions} table on startup.
 */
@Data
@ConfigurationProperties(prefix = "gen-adm")
public class GenAdmProperties {

    private List<PermissionDefinition> permissions = new ArrayList<>();

    /** Invitation expiry window in days, applied once at creation. */
    private int invitationTtlDays = 7;

    /** Data export retention window in days, applied once at creation. */
    private int exportTtlDays = 7;

    public record PermissionDefinition(String code, String description) {
    }
}
