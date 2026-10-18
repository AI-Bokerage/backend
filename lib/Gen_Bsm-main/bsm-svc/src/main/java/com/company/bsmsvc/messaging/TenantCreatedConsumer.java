package com.company.bsmsvc.messaging;

import com.company.bsmsvc.application.service.TenantOnboardingService;
import com.company.bsmsvc.domain.model.OnboardTenantCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consumes {@code tenant.created} events from TNT-SVC and delegates tenant
 * onboarding (subscription + first invoice) to {@link TenantOnboardingService}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCreatedConsumer {

    private final TenantOnboardingService tenantOnboardingService;

    @RabbitListener(queues = "${bsm.messaging.tenant-created-queue:q.bsm.tenant-created}")
    @Transactional
    public void onTenantCreated(TenantCreatedMessage event) {
        log.info("[TenantCreatedConsumer] tenant.created received tenantId={} planTier={} billingCycle={}",
            event.tenantId(), event.planTier(), event.billingCycle());
        tenantOnboardingService.onboard(new OnboardTenantCommand(
            event.tenantId(), event.billingCycle(), event.ppmPlanId(),
            event.ppmPlanVersionId(), event.region(), event.sourceChannel()));
    }
}
