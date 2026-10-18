package com.example.authsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Exchange names for two-tier audit-event routing, bound from
 * {@code app.messaging.audit.*}. Only read when {@code app.messaging.enabled=true}.
 * Defaults are generic — a host app wanting exact CPMS-Platform parity sets
 * {@code tenant-exchange=cpms.audit} / {@code platform-exchange=cpms.platform.audit}.
 */
@Data
@ConfigurationProperties(prefix = "app.messaging.audit")
public class AuditRoutingProperties {

    private String tenantExchange = "auth.audit.tenant";
    private String platformExchange = "auth.audit.platform";
}
