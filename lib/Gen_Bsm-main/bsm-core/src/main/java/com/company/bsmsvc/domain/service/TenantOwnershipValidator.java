package com.company.bsmsvc.domain.service;

import com.company.bsmsvc.domain.exception.BusinessRuleViolationException;
import com.company.bsmsvc.domain.model.Subscription;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Domain service that enforces tenant ownership of a subscription.
 * Applied before any mutation that targets a subscription by its ID.
 */
@Slf4j
@Component
public class TenantOwnershipValidator {

    public void validate(Subscription subscription, UUID requestedTenantId) {
        log.debug("[TenantOwnershipValidator] Validating ownership. subscriptionId={}, subscriptionTenantId={}, requestedTenantId={}",
            subscription.getId(), subscription.getTenantId(), requestedTenantId);

        if (requestedTenantId == null) {
            log.warn("[TenantOwnershipValidator] DENIED - requestedTenantId is null. subscriptionId={}", subscription.getId());
            throw new BusinessRuleViolationException("tenantId must be provided for ownership validation");
        }
        if (!subscription.getTenantId().equals(requestedTenantId)) {
            log.warn("[TenantOwnershipValidator] DENIED - tenant mismatch. subscriptionId={}, ownerTenantId={}, requestedTenantId={}",
                subscription.getId(), subscription.getTenantId(), requestedTenantId);
            throw new BusinessRuleViolationException(
                "Subscription " + subscription.getId() + " does not belong to tenant " + requestedTenantId
            );
        }

        log.debug("[TenantOwnershipValidator] PASSED. subscriptionId={}, tenantId={}", subscription.getId(), requestedTenantId);
    }
}
