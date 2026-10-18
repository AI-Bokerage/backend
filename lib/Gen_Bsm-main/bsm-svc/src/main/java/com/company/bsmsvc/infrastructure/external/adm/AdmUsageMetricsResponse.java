package com.company.bsmsvc.infrastructure.external.adm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Wire DTO matching ADM-SVC's {@code TenantUsageMetricsResponse}.
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} ensures forward
 * compatibility when ADM adds fields in future versions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdmUsageMetricsResponse(
    UUID   tenantId,
    long   activeInternalUsers,
    long   totalInternalUsers,
    long   activeClientUsers,
    long   totalClientUsers
) {}
