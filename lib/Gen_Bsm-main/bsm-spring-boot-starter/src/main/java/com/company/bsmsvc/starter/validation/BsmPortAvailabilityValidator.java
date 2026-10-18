package com.company.bsmsvc.starter.validation;

import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.PaymentMethodService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.application.service.TenantOnboardingService;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.DefaultTrialPlanPort;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.SubscriptionEventPublisherPort;
import com.company.bsmsvc.domain.port.SubscriptionEventRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionHistoryRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import com.company.bsmsvc.domain.port.SubscriptionScheduleRepositoryPort;
import com.company.bsmsvc.domain.port.TenantBillingProfileRepositoryPort;
import com.company.bsmsvc.domain.port.TenantScopePort;
import com.company.bsmsvc.domain.port.TenantTrialRecordRepositoryPort;
import com.company.bsmsvc.domain.port.UsageLimitsSeedingPort;
import com.company.bsmsvc.domain.port.UserUsagePort;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;

/**
 * Fails startup fast, with an actionable message, when a consumer has supplied SOME but not ALL
 * of an aggregate's required port beans. {@code @ConditionalOnBean} on each aggregate's
 * auto-configuration already handles the "none of these ports exist — skip silently, this
 * consumer doesn't want this aggregate" case; this validator exists specifically for the
 * "partial" case that {@code @ConditionalOnBean} cannot express: some anchor port is present
 * (signalling intent to use the aggregate) but the resulting service bean never got created,
 * meaning some OTHER required port is missing.
 *
 * <p>Runs via {@link SmartInitializingSingleton#afterSingletonsInstantiated()} — after every
 * other auto-configuration has had its chance to register beans, so the check reflects final
 * context state.
 */
public class BsmPortAvailabilityValidator implements SmartInitializingSingleton {

    private final ApplicationContext context;

    public BsmPortAvailabilityValidator(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<String> problems = new ArrayList<>();
        for (BsmAggregateSpec spec : aggregates()) {
            boolean anchorPresent = spec.anchorPorts().stream().anyMatch(this::hasBean);
            if (!anchorPresent) {
                continue; // consumer doesn't want this aggregate — nothing to validate
            }
            boolean serviceRegistered = spec.expectedServices().stream().anyMatch(this::hasBean);
            if (serviceRegistered) {
                continue; // fully wired
            }
            List<String> missing = spec.requiredPorts().stream()
                .filter(port -> !hasBean(port))
                .map(Class::getSimpleName)
                .toList();
            problems.add(
                "Aggregate '" + spec.aggregateName() + "' looks partially configured: at least one "
                + "anchor port (" + anchorNames(spec) + ") is present, but the following required "
                + "port bean(s) are missing, so its application service could not be created: "
                + missing + ". Either supply all of them, or remove all of them if you don't need "
                + "this aggregate."
            );
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                "BSM library startup validation failed:\n  - " + String.join("\n  - ", problems));
        }
    }

    private boolean hasBean(Class<?> type) {
        return context.getBeanNamesForType(type).length > 0;
    }

    private static String anchorNames(BsmAggregateSpec spec) {
        return spec.anchorPorts().stream().map(Class::getSimpleName)
            .reduce((a, b) -> a + "/" + b).orElse("");
    }

    private static List<BsmAggregateSpec> aggregates() {
        return List.of(
            new BsmAggregateSpec("tenant-billing",
                List.of(TenantBillingProfileRepositoryPort.class),
                List.of(TenantBillingProfileRepositoryPort.class, TenantScopePort.class),
                List.of(TenantBillingProfileService.class)),
            new BsmAggregateSpec("payment",
                List.of(PaymentGatewayResolver.class),
                List.of(PaymentGatewayResolver.class, PaymentMethodRepositoryPort.class,
                    PaymentRepositoryPort.class, PlatformInvoiceRepositoryPort.class,
                    TenantScopePort.class, EventPublisherPort.class),
                List.of(PaymentService.class, PaymentMethodService.class)),
            new BsmAggregateSpec("invoice",
                List.of(PlatformInvoiceRepositoryPort.class),
                List.of(PlatformInvoiceRepositoryPort.class, SubscriptionRepositoryPort.class,
                    TenantScopePort.class, EventPublisherPort.class),
                List.of(InvoiceService.class)),
            new BsmAggregateSpec("subscription",
                List.of(SubscriptionRepositoryPort.class),
                List.of(SubscriptionRepositoryPort.class, SubscriptionHistoryRepositoryPort.class,
                    SubscriptionEventRepositoryPort.class, SubscriptionScheduleRepositoryPort.class,
                    TenantTrialRecordRepositoryPort.class, UsageLimitsSeedingPort.class,
                    UserUsagePort.class, TenantScopePort.class, SubscriptionEventPublisherPort.class,
                    PaymentMethodRepositoryPort.class, PaymentGatewayResolver.class),
                List.of(SubscriptionService.class)),
            new BsmAggregateSpec("dunning",
                List.of(DunningAttemptRepositoryPort.class),
                List.of(DunningAttemptRepositoryPort.class, DunningEventPublisher.class,
                    BillingLedgerRepositoryPort.class, PaymentMethodRepositoryPort.class,
                    PaymentRepositoryPort.class, TenantScopePort.class,
                    SubscriptionEventPublisherPort.class, EventPublisherPort.class),
                List.of(DunningService.class)),
            new BsmAggregateSpec("tenant-onboarding",
                List.of(DefaultTrialPlanPort.class, PlanVersionMetaPort.class, PpmPricingService.class),
                List.of(DefaultTrialPlanPort.class, PlanVersionMetaPort.class, PpmPricingService.class,
                    SubscriptionRepositoryPort.class),
                List.of(TenantOnboardingService.class))
        );
    }
}
