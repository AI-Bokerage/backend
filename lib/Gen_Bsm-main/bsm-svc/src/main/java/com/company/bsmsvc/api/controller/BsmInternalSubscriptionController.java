package com.company.bsmsvc.api.controller;

import com.company.bsmsvc.api.dto.response.ApiResponse;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.infrastructure.client.ppm.PpmVersionMetaClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal BSM endpoints consumed by TNT-SVC for reconciliation.
 * Protected by {@code BsmInternalSecretFilter} (X-Internal-Secret header).
 * No JWT required — these paths are declared as permitAll() in SecurityConfig.
 */
@RestController
@RequestMapping("/internal/v1/bsm/subscriptions")
@RequiredArgsConstructor
@Tag(name = "Internal Subscriptions", description = "Internal subscription query endpoints for service-to-service use")
public class BsmInternalSubscriptionController {

    private final SubscriptionRepositoryPort subscriptionRepository;
    private final PpmVersionMetaClient ppmVersionMetaClient;

    @GetMapping("/{tenantId}")
    @Operation(summary = "Get active subscription for tenant (internal)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSubscription(@PathVariable UUID tenantId) {
        return subscriptionRepository.findCurrentByTenantId(tenantId)
            .map(sub -> ResponseEntity.ok(ApiResponse.ok("Subscription found", buildSummary(sub))))
            .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No subscription found", null)));
    }

    @GetMapping("/{tenantId}/status")
    @Operation(summary = "Get subscription status for tenant (internal)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus(@PathVariable UUID tenantId) {
        return subscriptionRepository.findCurrentByTenantId(tenantId)
            .map(sub -> {
                Map<String, Object> status = Map.of(
                    "subscriptionId", sub.getId(),
                    "tenantId", sub.getTenantId(),
                    "status", sub.getStatus().name(),
                    "dunningStatus", sub.getDunningStatus() != null ? sub.getDunningStatus().name() : "NORMAL"
                );
                return ResponseEntity.ok(ApiResponse.ok("Status found", status));
            })
            .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No subscription", Map.of("status", "NONE"))));
    }

    @GetMapping("/{tenantId}/summary")
    @Operation(summary = "Get billing summary for tenant (internal)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSummary(@PathVariable UUID tenantId) {
        return subscriptionRepository.findCurrentByTenantId(tenantId)
            .map(sub -> {
                String planCode = resolvePlanCode(sub);
                Map<String, Object> summary = new java.util.LinkedHashMap<>();
                summary.put("subscriptionId", sub.getId());
                summary.put("tenantId", sub.getTenantId());
                summary.put("status", sub.getStatus().name());
                summary.put("planCode", planCode);
                summary.put("ppmPlanId", sub.getPpmPlanId());
                summary.put("billingCycle", sub.getBillingCycle() != null ? sub.getBillingCycle().name() : null);
                summary.put("trialEndsAt", sub.getTrialEndsAt());
                summary.put("currentPeriodStart", sub.getCurrentPeriodStart());
                summary.put("currentPeriodEnd", sub.getCurrentPeriodEnd());
                summary.put("dunningStatus", sub.getDunningStatus() != null ? sub.getDunningStatus().name() : "NORMAL");
                return ResponseEntity.ok(ApiResponse.ok("Summary found", summary));
            })
            .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok("No subscription", null)));
    }

    private Map<String, Object> buildSummary(Subscription sub) {
        String planCode = resolvePlanCode(sub);
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("subscriptionId", sub.getId());
        m.put("tenantId", sub.getTenantId());
        m.put("status", sub.getStatus().name());
        m.put("planCode", planCode);
        m.put("billingCycle", sub.getBillingCycle() != null ? sub.getBillingCycle().name() : null);
        m.put("trialEndsAt", sub.getTrialEndsAt());
        m.put("currentPeriodEnd", sub.getCurrentPeriodEnd());
        return m;
    }

    private String resolvePlanCode(Subscription sub) {
        if (sub.getPpmPlanVersionId() != null) {
            try {
                return ppmVersionMetaClient.getVersionMeta(sub.getPpmPlanVersionId()).planCode();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
