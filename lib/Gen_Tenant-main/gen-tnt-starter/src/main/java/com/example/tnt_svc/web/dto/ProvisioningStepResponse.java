// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/ProvisioningStepResponse.java
package com.example.tnt_svc.web.dto;

import com.example.tnt_svc.domain.ProvisioningStep;
import com.example.tnt_svc.domain.ProvisioningStepStatus;

import java.util.UUID;

public record ProvisioningStepResponse(
    UUID id,
    String stepName,
    int stepOrder,
    ProvisioningStepStatus status,
    int retryCount,
    String errorMessage
) {
    public static ProvisioningStepResponse from(ProvisioningStep step) {
        return new ProvisioningStepResponse(
            step.getId(), step.getStepName(), step.getStepOrder(), step.getStatus(),
            step.getRetryCount(), step.getErrorMessage());
    }
}
