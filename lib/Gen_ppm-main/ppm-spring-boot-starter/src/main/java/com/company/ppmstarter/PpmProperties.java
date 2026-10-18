package com.company.ppmstarter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Central configuration namespace for {@code ppm-spring-boot-starter}.
 *
 * <p>Kept deliberately small today — a single master switch — but this is
 * the one place future starter configuration should live. Do not introduce
 * scattered {@code @Value} lookups elsewhere in the starter; add a field
 * here instead, even if it starts with just one option.
 */
@ConfigurationProperties(prefix = "ppm")
public class PpmProperties {

    /**
     * Master switch for the entire starter. When {@code false}, no ppm-core
     * use-case bean is registered and no repository-port validation runs —
     * equivalent to not having the starter on the classpath at all, without
     * having to remove the dependency. Defaults to {@code true}.
     */
    private boolean enabled = true;

    private final Catalog catalog = new Catalog();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Catalog getCatalog() {
        return catalog;
    }

    /**
     * {@code CatalogQueryService} is a composite read service spanning five
     * aggregates (Plan, PlanVersion, PlanModule, Module, PlanEntitlement,
     * Entitlement) — a much larger dependency footprint than its name
     * suggests, and one that {@code ppm-demo}'s Phase 5 validation found
     * surprises consumers who scan for it by accident. It is therefore
     * opt-in, not auto-registered by default like every other aggregate.
     */
    public static class Catalog {

        /** Set {@code true} to auto-register {@code CatalogQueryService} when all six required ports are present. */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
