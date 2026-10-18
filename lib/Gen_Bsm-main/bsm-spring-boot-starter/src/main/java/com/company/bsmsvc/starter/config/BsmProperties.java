package com.company.bsmsvc.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration surface for the BSM Spring Boot starter, prefix {@code bsm}.
 *
 * <p>Only exposes what bsm-core's already-established policy value objects need
 * ({@link com.company.bsmsvc.domain.model.DunningPolicy}, {@link com.company.bsmsvc.domain.model.TrialPolicy},
 * {@link com.company.bsmsvc.domain.model.ReconciliationPolicy}) plus a master switch. No
 * "commercial module" / "dashboard module" toggles are exposed here — those services
 * ({@code CommercialEngineService}, {@code BillingDashboardService}) are not part of bsm-core;
 * see ARCHITECTURE_CERTIFICATION.md for why, and STARTER_GUIDE.md for the finding.
 */
@ConfigurationProperties(prefix = "bsm")
public class BsmProperties {

    /** Master switch — set {@code bsm.enabled=false} to disable all auto-configuration. */
    private boolean enabled = true;

    private final Dunning dunning = new Dunning();
    private final Trial trial = new Trial();
    private final Reconciliation reconciliation = new Reconciliation();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Dunning getDunning() { return dunning; }
    public Trial getTrial() { return trial; }
    public Reconciliation getReconciliation() { return reconciliation; }

    public static class Dunning {
        private int day1RetryAfterHours = 24;
        private int day3RetryAfterHours = 72;
        private int day7RetryAfterHours = 168;
        private int suspendAfterDays = 14;
        private int cancelAfterDays = 30;

        public int getDay1RetryAfterHours() { return day1RetryAfterHours; }
        public void setDay1RetryAfterHours(int v) { this.day1RetryAfterHours = v; }
        public int getDay3RetryAfterHours() { return day3RetryAfterHours; }
        public void setDay3RetryAfterHours(int v) { this.day3RetryAfterHours = v; }
        public int getDay7RetryAfterHours() { return day7RetryAfterHours; }
        public void setDay7RetryAfterHours(int v) { this.day7RetryAfterHours = v; }
        public int getSuspendAfterDays() { return suspendAfterDays; }
        public void setSuspendAfterDays(int v) { this.suspendAfterDays = v; }
        public int getCancelAfterDays() { return cancelAfterDays; }
        public void setCancelAfterDays(int v) { this.cancelAfterDays = v; }
    }

    public static class Trial {
        private int defaultDays = 14;

        public int getDefaultDays() { return defaultDays; }
        public void setDefaultDays(int v) { this.defaultDays = v; }
    }

    public static class Reconciliation {
        private int thresholdSeconds = 300;

        public int getThresholdSeconds() { return thresholdSeconds; }
        public void setThresholdSeconds(int v) { this.thresholdSeconds = v; }
    }
}
