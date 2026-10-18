// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/TenantResponse.java
package com.example.tnt_svc.web.dto;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
    UUID id,
    String slug,
    String name,
    TenantStatus status,
    String region,
    UUID primaryOwnerUserId,
    UUID provisioningJobId,
    Instant createdAt
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
            tenant.getId(),
            tenant.getSlug(),
            tenant.getName(),
            tenant.getStatus(),
            tenant.getRegion(),
            tenant.getPrimaryOwnerUserId(),
            tenant.getProvisioningJobId(),
            tenant.getCreatedAt()
        );
    }
}
