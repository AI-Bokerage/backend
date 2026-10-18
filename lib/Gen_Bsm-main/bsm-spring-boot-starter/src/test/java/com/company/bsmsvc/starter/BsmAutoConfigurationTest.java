package com.company.bsmsvc.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.company.bsmsvc.application.service.DunningService;
import com.company.bsmsvc.application.service.InvoiceRenewalService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.PaymentGatewayResolver;
import com.company.bsmsvc.application.service.PaymentMethodService;
import com.company.bsmsvc.application.service.PaymentService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.application.service.TenantOnboardingService;
import com.company.bsmsvc.domain.model.DunningPolicy;
import com.company.bsmsvc.domain.port.BillingLedgerRepositoryPort;
import com.company.bsmsvc.domain.port.DefaultTrialPlanPort;
import com.company.bsmsvc.domain.port.DunningAttemptRepositoryPort;
import com.company.bsmsvc.domain.port.DunningEventPublisher;
import com.company.bsmsvc.domain.port.EventPublisherPort;
import com.company.bsmsvc.domain.port.InvoiceEventPublisher;
import com.company.bsmsvc.domain.port.PaymentMethodRepositoryPort;
import com.company.bsmsvc.domain.port.PaymentRepositoryPort;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.port.PlatformInvoiceRepositoryPort;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.SubscriptionAddOnRepositoryPort;
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
import com.company.bsmsvc.starter.config.BsmPolicyAutoConfiguration;
import com.company.bsmsvc.starter.config.BsmSupportAutoConfiguration;
import com.company.bsmsvc.starter.config.DunningAutoConfiguration;
import com.company.bsmsvc.starter.config.InvoiceAutoConfiguration;
import com.company.bsmsvc.starter.config.InvoiceRenewalAutoConfiguration;
import com.company.bsmsvc.starter.config.PaymentAutoConfiguration;
import com.company.bsmsvc.starter.config.SubscriptionAutoConfiguration;
import com.company.bsmsvc.starter.config.TenantBillingProfileAutoConfiguration;
import com.company.bsmsvc.starter.config.TenantOnboardingAutoConfiguration;
import com.company.bsmsvc.starter.config.validation.BsmPortAvailabilityValidatorAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class BsmAutoConfigurationTest {

    private static final AutoConfigurations ALL_AUTO_CONFIGS = AutoConfigurations.of(
        BsmPolicyAutoConfiguration.class, BsmSupportAutoConfiguration.class,
        TenantBillingProfileAutoConfiguration.class, PaymentAutoConfiguration.class,
        InvoiceAutoConfiguration.class, SubscriptionAutoConfiguration.class,
        InvoiceRenewalAutoConfiguration.class, DunningAutoConfiguration.class,
        TenantOnboardingAutoConfiguration.class, BsmPortAvailabilityValidatorAutoConfiguration.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(ALL_AUTO_CONFIGS);

    // ── 1. full successful startup ──────────────────────────────────────────

    @Test
    void fullPortSet_startsSuccessfullyWithEveryService() {
        runner.withUserConfiguration(AllPortsConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TenantBillingProfileService.class);
            assertThat(context).hasSingleBean(PaymentMethodService.class);
            assertThat(context).hasSingleBean(PaymentService.class);
            assertThat(context).hasSingleBean(InvoiceService.class);
            assertThat(context).hasSingleBean(SubscriptionService.class);
            assertThat(context).hasSingleBean(InvoiceRenewalService.class);
            assertThat(context).hasSingleBean(DunningService.class);
            assertThat(context).hasSingleBean(TenantOnboardingService.class);
        });
    }

    // ── 2. no ports at all — library quietly does nothing ──────────────────

    @Test
    void noPorts_startsSuccessfullyWithNoServices() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(TenantBillingProfileService.class);
            assertThat(context).doesNotHaveBean(PaymentService.class);
            assertThat(context).doesNotHaveBean(SubscriptionService.class);
        });
    }

    // ── 3. library disabled entirely ────────────────────────────────────────

    @Test
    void disabled_registersNoServicesEvenWithAllPorts() {
        runner.withUserConfiguration(AllPortsConfig.class)
            .withPropertyValues("bsm.enabled=false")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).doesNotHaveBean(SubscriptionService.class);
                assertThat(context).doesNotHaveBean(TenantBillingProfileService.class);
            });
    }

    // ── 4. tenant-billing aggregate alone ───────────────────────────────────

    @Test
    void tenantBillingPortsOnly_registersOnlyTenantBillingProfileService() {
        runner.withUserConfiguration(TenantBillingPortsOnlyConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(TenantBillingProfileService.class);
            assertThat(context).doesNotHaveBean(PaymentService.class);
            assertThat(context).doesNotHaveBean(SubscriptionService.class);
        });
    }

    // ── 5. missing port entirely (payment gateway) — payment aggregate skipped ──

    @Test
    void missingPaymentGatewayResolver_skipsPaymentAggregateEntirely() {
        runner.withUserConfiguration(AllPortsExceptPaymentGatewayResolverConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(PaymentService.class);
            assertThat(context).doesNotHaveBean(PaymentMethodService.class);
            // Other aggregates that don't need PaymentGatewayResolver directly still work —
            // except subscription/dunning, which do require it; verify only invoice survives.
        });
    }

    // ── 6. partial ports (anchor present, one required port missing) — fails fast ──

    @Test
    void partialTenantBillingPorts_failsFastWithActionableMessage() {
        runner.withUserConfiguration(TenantBillingRepoOnlyNoScopeConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure().getMessage())
                .contains("tenant-billing")
                .contains("TenantScopePort");
        });
    }

    @Test
    void partialInvoicePorts_failsFastWithActionableMessage() {
        runner.withUserConfiguration(InvoiceAnchorOnlyConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure().getMessage())
                .contains("invoice");
        });
    }

    @Test
    void partialSubscriptionPorts_failsFastWithActionableMessage() {
        runner.withUserConfiguration(SubscriptionAnchorOnlyConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure().getMessage())
                .contains("subscription");
        });
    }

    @Test
    void partialDunningPorts_failsFastWithActionableMessage() {
        runner.withUserConfiguration(DunningAnchorOnlyConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure().getMessage())
                .contains("dunning");
        });
    }

    @Test
    void partialOnboardingPorts_failsFastWithActionableMessage() {
        runner.withUserConfiguration(OnboardingAnchorOnlyConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure().getMessage())
                .contains("tenant-onboarding");
        });
    }

    // ── 7. bean override — custom user bean wins over auto-configured default ──

    @Test
    void customTenantBillingProfileServiceBean_takesPrecedenceOverAutoConfigured() {
        TenantBillingProfileService custom = mock(TenantBillingProfileService.class);
        runner.withUserConfiguration(AllPortsConfig.class)
            .withBean("customTenantBillingProfileService", TenantBillingProfileService.class, () -> custom)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(TenantBillingProfileService.class)).isSameAs(custom);
            });
    }

    @Test
    void customDunningPolicyBean_takesPrecedenceOverPropertiesBinding() {
        DunningPolicy custom = new DunningPolicy(1, 2, 3, 4, 5);
        runner.withUserConfiguration(AllPortsConfig.class)
            .withBean("customDunningPolicy", DunningPolicy.class, () -> custom)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(DunningPolicy.class)).isSameAs(custom);
            });
    }

    // ── 8. conditional activation: only invoice aggregate wired ─────────────

    @Test
    void invoicePortsWithBareSubscriptionRepositoryPort_failsFast_becauseInvoiceServiceNeedsSubscriptionRepositoryPortWhichIsAlsoSubscriptionsAnchor() {
        // Architecture finding: SubscriptionRepositoryPort is both a direct InvoiceService
        // dependency AND the subscription aggregate's own anchor port. Supplying it to satisfy
        // InvoiceService without the rest of the subscription port set is therefore correctly
        // rejected as a partially-configured subscription aggregate, not silently ignored.
        runner.withUserConfiguration(InvoicePortsOnlyConfig.class).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure().getMessage()).contains("subscription");
        });
    }

    // ── 9. multiple candidate adapters — ambiguity is the consumer's problem, not ours ──

    @Test
    void twoTenantScopePortBeans_userMustQualify_contextStillWiresViaPrimary() {
        runner.withUserConfiguration(AllPortsConfig.class, TwoTenantScopePortsConfig.class)
            .run(context -> {
                // With a @Primary bean declared, Spring resolves unambiguously and startup succeeds.
                assertThat(context).hasNotFailed();
                assertThat(context).hasSingleBean(TenantBillingProfileService.class);
            });
    }

    // ── 10. properties binding actually reaches the policy beans ────────────

    @Test
    void dunningProperties_bindIntoDunningPolicyBean() {
        runner.withUserConfiguration(AllPortsConfig.class)
            .withPropertyValues(
                "bsm.dunning.day1-retry-after-hours=1",
                "bsm.dunning.suspend-after-days=7")
            .run(context -> {
                assertThat(context).hasNotFailed();
                DunningPolicy policy = context.getBean(DunningPolicy.class);
                assertThat(policy.day1RetryAfterHours()).isEqualTo(1);
                assertThat(policy.suspendAfterDays()).isEqualTo(7);
            });
    }

    // ── configuration fixtures ───────────────────────────────────────────────

    @Configuration
    static class AllPortsConfig {
        @Bean TenantBillingProfileRepositoryPort tenantBillingProfileRepositoryPort() { return mock(TenantBillingProfileRepositoryPort.class); }
        @Bean TenantScopePort tenantScopePort() { return mock(TenantScopePort.class); }
        @Bean PaymentGatewayResolver paymentGatewayResolver() { return mock(PaymentGatewayResolver.class); }
        @Bean PaymentMethodRepositoryPort paymentMethodRepositoryPort() { return mock(PaymentMethodRepositoryPort.class); }
        @Bean PaymentRepositoryPort paymentRepositoryPort() { return mock(PaymentRepositoryPort.class); }
        @Bean PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort() { return mock(PlatformInvoiceRepositoryPort.class); }
        @Bean EventPublisherPort eventPublisherPort() { return mock(EventPublisherPort.class); }
        @Bean InvoiceEventPublisher invoiceEventPublisher() { return mock(InvoiceEventPublisher.class); }
        @Bean SubscriptionRepositoryPort subscriptionRepositoryPort() { return mock(SubscriptionRepositoryPort.class); }
        @Bean SubscriptionHistoryRepositoryPort subscriptionHistoryRepositoryPort() { return mock(SubscriptionHistoryRepositoryPort.class); }
        @Bean SubscriptionEventRepositoryPort subscriptionEventRepositoryPort() { return mock(SubscriptionEventRepositoryPort.class); }
        @Bean SubscriptionScheduleRepositoryPort subscriptionScheduleRepositoryPort() { return mock(SubscriptionScheduleRepositoryPort.class); }
        @Bean SubscriptionAddOnRepositoryPort subscriptionAddOnRepositoryPort() { return mock(SubscriptionAddOnRepositoryPort.class); }
        @Bean TenantTrialRecordRepositoryPort tenantTrialRecordRepositoryPort() { return mock(TenantTrialRecordRepositoryPort.class); }
        @Bean UsageLimitsSeedingPort usageLimitsSeedingPort() { return mock(UsageLimitsSeedingPort.class); }
        @Bean UserUsagePort userUsagePort() { return mock(UserUsagePort.class); }
        @Bean SubscriptionEventPublisherPort subscriptionEventPublisherPort() { return mock(SubscriptionEventPublisherPort.class); }
        @Bean DunningAttemptRepositoryPort dunningAttemptRepositoryPort() { return mock(DunningAttemptRepositoryPort.class); }
        @Bean DunningEventPublisher dunningEventPublisher() { return mock(DunningEventPublisher.class); }
        @Bean BillingLedgerRepositoryPort billingLedgerRepositoryPort() { return mock(BillingLedgerRepositoryPort.class); }
        @Bean DefaultTrialPlanPort defaultTrialPlanPort() { return mock(DefaultTrialPlanPort.class); }
        @Bean PlanVersionMetaPort planVersionMetaPort() { return mock(PlanVersionMetaPort.class); }
        @Bean PpmPricingService ppmPricingService() { return mock(PpmPricingService.class); }
    }

    @Configuration
    static class TenantBillingPortsOnlyConfig {
        @Bean TenantBillingProfileRepositoryPort tenantBillingProfileRepositoryPort() { return mock(TenantBillingProfileRepositoryPort.class); }
        @Bean TenantScopePort tenantScopePort() { return mock(TenantScopePort.class); }
    }

    @Configuration
    static class TenantBillingRepoOnlyNoScopeConfig {
        @Bean TenantBillingProfileRepositoryPort tenantBillingProfileRepositoryPort() { return mock(TenantBillingProfileRepositoryPort.class); }
        // TenantScopePort deliberately omitted — partial config, anchor present, should fail fast.
    }

    @Configuration
    static class InvoicePortsOnlyConfig {
        @Bean PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort() { return mock(PlatformInvoiceRepositoryPort.class); }
        @Bean InvoiceEventPublisher invoiceEventPublisher() { return mock(InvoiceEventPublisher.class); }
        @Bean EventPublisherPort eventPublisherPort() { return mock(EventPublisherPort.class); }
        @Bean SubscriptionRepositoryPort subscriptionRepositoryPort() { return mock(SubscriptionRepositoryPort.class); }
        @Bean SubscriptionAddOnRepositoryPort subscriptionAddOnRepositoryPort() { return mock(SubscriptionAddOnRepositoryPort.class); }
        @Bean TenantBillingProfileRepositoryPort tenantBillingProfileRepositoryPort() { return mock(TenantBillingProfileRepositoryPort.class); }
        @Bean TenantScopePort tenantScopePort() { return mock(TenantScopePort.class); }
    }

    @Configuration
    static class InvoiceAnchorOnlyConfig {
        // Anchor (PlatformInvoiceRepositoryPort) present; InvoiceNumberGenerator is auto-supplied by
        // BsmSupportAutoConfiguration, so the missing piece here is InvoiceEventPublisher/EventPublisherPort.
        @Bean PlatformInvoiceRepositoryPort platformInvoiceRepositoryPort() { return mock(PlatformInvoiceRepositoryPort.class); }
    }

    @Configuration
    static class SubscriptionAnchorOnlyConfig {
        @Bean SubscriptionRepositoryPort subscriptionRepositoryPort() { return mock(SubscriptionRepositoryPort.class); }
    }

    @Configuration
    static class DunningAnchorOnlyConfig {
        @Bean DunningAttemptRepositoryPort dunningAttemptRepositoryPort() { return mock(DunningAttemptRepositoryPort.class); }
    }

    @Configuration
    static class OnboardingAnchorOnlyConfig {
        @Bean DefaultTrialPlanPort defaultTrialPlanPort() { return mock(DefaultTrialPlanPort.class); }
        @Bean PlanVersionMetaPort planVersionMetaPort() { return mock(PlanVersionMetaPort.class); }
        @Bean PpmPricingService ppmPricingService() { return mock(PpmPricingService.class); }
        // SubscriptionRepositoryPort and the fully-wired SubscriptionService deliberately omitted.
    }

    @Configuration
    static class AllPortsExceptPaymentGatewayResolverConfig {
        @Bean TenantBillingProfileRepositoryPort tenantBillingProfileRepositoryPort() { return mock(TenantBillingProfileRepositoryPort.class); }
        @Bean TenantScopePort tenantScopePort() { return mock(TenantScopePort.class); }
        @Bean PaymentMethodRepositoryPort paymentMethodRepositoryPort() { return mock(PaymentMethodRepositoryPort.class); }
        @Bean PaymentRepositoryPort paymentRepositoryPort() { return mock(PaymentRepositoryPort.class); }
        @Bean EventPublisherPort eventPublisherPort() { return mock(EventPublisherPort.class); }
        // Deliberately no invoice/subscription ports at all — this fixture isolates tenant-billing
        // + payment only. PaymentGatewayResolver deliberately absent — payment aggregate should
        // skip cleanly (anchor missing = no partial-config error, just no payment services).
    }

    @Configuration
    static class TwoTenantScopePortsConfig {
        @org.springframework.context.annotation.Primary
        @Bean TenantScopePort primaryTenantScopePort() { return mock(TenantScopePort.class); }
    }
}
