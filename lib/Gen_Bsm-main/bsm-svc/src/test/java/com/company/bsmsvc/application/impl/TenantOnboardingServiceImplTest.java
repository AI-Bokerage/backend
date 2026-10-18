package com.company.bsmsvc.application.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.company.bsmsvc.application.service.InvoiceGenerationService;
import com.company.bsmsvc.application.service.InvoiceService;
import com.company.bsmsvc.application.service.SubscriptionService;
import com.company.bsmsvc.application.service.TenantBillingProfileService;
import com.company.bsmsvc.domain.enums.SubscriptionStatus;
import com.company.bsmsvc.domain.exception.TenantBillingProfileNotFoundException;
import com.company.bsmsvc.domain.model.OnboardTenantCommand;
import com.company.bsmsvc.domain.model.PlatformInvoice;
import com.company.bsmsvc.domain.model.PpmDefaultTrialResult;
import com.company.bsmsvc.domain.model.PpmResolvePriceResult;
import com.company.bsmsvc.domain.model.PpmVersionMetaResult;
import com.company.bsmsvc.domain.model.Subscription;
import com.company.bsmsvc.domain.model.TenantBillingProfile;
import com.company.bsmsvc.domain.model.TrialPolicy;
import com.company.bsmsvc.domain.port.DefaultTrialPlanPort;
import com.company.bsmsvc.domain.port.PlanVersionMetaPort;
import com.company.bsmsvc.domain.port.PpmPricingService;
import com.company.bsmsvc.domain.port.SubscriptionRepositoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantOnboardingServiceImplTest {

    @Mock SubscriptionRepositoryPort subscriptionRepository;
    @Mock SubscriptionService subscriptionService;
    @Mock DefaultTrialPlanPort defaultTrialPlanPort;
    @Mock PlanVersionMetaPort planVersionMetaPort;
    @Mock InvoiceGenerationService invoiceGenerationService;
    @Mock InvoiceService invoiceService;
    @Mock TenantBillingProfileService tenantBillingProfileService;
    @Mock PpmPricingService ppmPricingService;

    TenantOnboardingServiceImpl service;

    static final UUID TENANT_ID = UUID.randomUUID();
    static final UUID PPM_PLAN_ID = UUID.randomUUID();
    static final UUID PPM_PLAN_VERSION_ID = UUID.randomUUID();
    static final UUID INVOICE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TenantOnboardingServiceImpl(
            subscriptionRepository, subscriptionService, defaultTrialPlanPort, planVersionMetaPort,
            invoiceGenerationService, invoiceService, tenantBillingProfileService, ppmPricingService,
            new TrialPolicy(14));
    }

    @Test
    void onboard_skipsWhenSubscriptionAlreadyExists() {
        when(subscriptionRepository.findCurrentByTenantId(TENANT_ID))
            .thenReturn(Optional.of(Subscription.builder().id(UUID.randomUUID()).build()));

        service.onboard(new OnboardTenantCommand(TENANT_ID, "MONTHLY", null, null, "INDIA", "SELF_SIGNUP"));

        verify(subscriptionService, never()).createSubscription(any(), any(), any(), any());
    }

    @Test
    void onboard_noPreSelectedPlan_usesDefaultTrialAndGrantsZeroDollarPaidInvoice() {
        when(subscriptionRepository.findCurrentByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(defaultTrialPlanPort.getDefaultTrialPlan())
            .thenReturn(new PpmDefaultTrialResult(PPM_PLAN_ID, "FREE", PPM_PLAN_VERSION_ID));
        Subscription created = Subscription.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).status(SubscriptionStatus.TRIALING)
            .billingCycle(com.company.bsmsvc.domain.enums.BillingCycle.MONTHLY).currentPeriodStart(java.time.Instant.now()).currentPeriodEnd(java.time.Instant.now().plusSeconds(86400)).build();
        when(subscriptionService.createSubscription(any(), any(), any(), any())).thenReturn(created);
        PlatformInvoice invoice = PlatformInvoice.builder().id(INVOICE_ID).build();
        when(invoiceGenerationService.generateInvoice(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(invoice);

        service.onboard(new OnboardTenantCommand(TENANT_ID, "MONTHLY", null, null, "INDIA", "SELF_SIGNUP"));

        verify(subscriptionService).createSubscription(any(), org.mockito.ArgumentMatchers.eq(14), any(), any());
        verify(invoiceService).applyPayment(INVOICE_ID, null, null);
        verify(ppmPricingService, never()).resolvePrice(any(), any(), any(), any());
    }

    @Test
    void onboard_preSelectedPaidPlan_resolvesPriceAndLeavesInvoiceOpenForAdminProvisioned() {
        when(subscriptionRepository.findCurrentByTenantId(TENANT_ID)).thenReturn(Optional.empty());
        when(planVersionMetaPort.getVersionMeta(PPM_PLAN_VERSION_ID))
            .thenReturn(new PpmVersionMetaResult(PPM_PLAN_VERSION_ID, PPM_PLAN_ID, "STARTER", 1,
                true, true, LocalDate.now(), null, "starter"));
        when(tenantBillingProfileService.getProfile(TENANT_ID))
            .thenThrow(new TenantBillingProfileNotFoundException("none"));
        when(tenantBillingProfileService.createProfile(any(), any(), any(), any()))
            .thenReturn(TenantBillingProfile.builder().currency("INR").build());
        when(ppmPricingService.resolvePrice(any(), any(), any(), any()))
            .thenReturn(new PpmResolvePriceResult(PPM_PLAN_ID, UUID.randomUUID(), "monthly", "INR",
                "INDIA", new BigDecimal("999.00"), false, LocalDate.now(), true, 1L));
        Subscription created = Subscription.builder()
            .id(UUID.randomUUID()).tenantId(TENANT_ID).status(SubscriptionStatus.ACTIVE)
            .billingCycle(com.company.bsmsvc.domain.enums.BillingCycle.MONTHLY).currentPeriodStart(java.time.Instant.now()).currentPeriodEnd(java.time.Instant.now().plusSeconds(86400)).build();
        when(subscriptionService.createSubscription(any(), any(), any(), any())).thenReturn(created);
        PlatformInvoice invoice = PlatformInvoice.builder().id(INVOICE_ID).build();
        when(invoiceGenerationService.generateInvoice(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(invoice);

        service.onboard(new OnboardTenantCommand(
            TENANT_ID, "MONTHLY", PPM_PLAN_ID, PPM_PLAN_VERSION_ID, "INDIA", "SUP_SVC_PROVISIONED"));

        verify(subscriptionService).createSubscription(any(), org.mockito.ArgumentMatchers.eq(null), any(), any());
        verify(invoiceService, never()).applyPayment(any(), any(), any());
    }
}
