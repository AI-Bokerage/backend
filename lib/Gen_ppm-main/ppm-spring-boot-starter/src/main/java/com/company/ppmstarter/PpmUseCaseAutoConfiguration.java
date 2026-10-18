package com.company.ppmstarter;

import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.addon.usecase.AddOnApplicationService;
import com.company.ppmsvc.addon.usecase.AddOnApplicationServiceImpl;
import com.company.ppmsvc.addonprice.port.AddOnPriceRepositoryPort;
import com.company.ppmsvc.addonprice.usecase.AddOnPriceApplicationService;
import com.company.ppmsvc.addonprice.usecase.AddOnPriceApplicationServiceImpl;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.entitlement.usecase.EntitlementApplicationService;
import com.company.ppmsvc.entitlement.usecase.EntitlementApplicationServiceImpl;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.module.usecase.ModuleApplicationService;
import com.company.ppmsvc.module.usecase.ModuleApplicationServiceImpl;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import com.company.ppmsvc.plan.usecase.CatalogQueryService;
import com.company.ppmsvc.plan.usecase.CatalogQueryServiceImpl;
import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import com.company.ppmsvc.plan.usecase.PlanApplicationServiceImpl;
import com.company.ppmsvc.plan.usecase.PlanVersionApplicationService;
import com.company.ppmsvc.plan.usecase.PlanVersionApplicationServiceImpl;
import com.company.ppmsvc.planaddon.port.PlanAddOnRepositoryPort;
import com.company.ppmsvc.planaddon.usecase.PlanAddOnApplicationService;
import com.company.ppmsvc.planaddon.usecase.PlanAddOnApplicationServiceImpl;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import com.company.ppmsvc.planentitlement.usecase.DefaultEntitlementResolver;
import com.company.ppmsvc.planentitlement.usecase.EntitlementResolver;
import com.company.ppmsvc.planentitlement.usecase.PlanEntitlementApplicationService;
import com.company.ppmsvc.planentitlement.usecase.PlanEntitlementApplicationServiceImpl;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
import com.company.ppmsvc.planmodule.usecase.PlanModuleApplicationService;
import com.company.ppmsvc.planmodule.usecase.PlanModuleApplicationServiceImpl;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.planprice.usecase.PlanPriceApplicationService;
import com.company.ppmsvc.planprice.usecase.PlanPriceApplicationServiceImpl;
import com.company.ppmsvc.planprice.usecase.PricingResolver;
import com.company.ppmsvc.planprice.usecase.PricingResolverImpl;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import com.company.ppmsvc.promocode.usecase.PromoCodeApplicationService;
import com.company.ppmsvc.promocode.usecase.PromoCodeApplicationServiceImpl;
import com.company.ppmsvc.promocode.usecase.PromoValidationService;
import com.company.ppmsvc.promocode.usecase.PromoValidationServiceImpl;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
import com.company.ppmsvc.promocodeplan.usecase.PromoCodePlanApplicationService;
import com.company.ppmsvc.promocodeplan.usecase.PromoCodePlanApplicationServiceImpl;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers a ppm-core use-case bean for every aggregate whose required
 * repository port(s) are all present as beans in this application context —
 * eliminating the manual {@code @Bean PlanApplicationService planService(...)}
 * wiring a consumer would otherwise write by hand for each one.
 *
 * <p>Every method here follows the same two-condition shape:
 * <ul>
 *   <li>{@code @ConditionalOnBean} — only wire this use case when its
 *       required port(s) exist. An aggregate a consumer doesn't want simply
 *       never gets its use-case bean; see {@link PpmPortAvailabilityValidator}
 *       for the one case this silence does NOT cover (a partially-implemented
 *       aggregate).</li>
 *   <li>{@code @ConditionalOnMissingBean} — a consumer's own
 *       {@code @Bean PlanApplicationService(...)} always wins over this
 *       default, per standard Spring Boot auto-configuration convention.</li>
 * </ul>
 *
 * <p>Contains no business logic — every method is a one-line constructor
 * call into a {@code ppm-core} {@code Impl} class.
 */
@AutoConfiguration
@EnableConfigurationProperties(PpmProperties.class)
public class PpmUseCaseAutoConfiguration {

    // ── Plan (+ PlanVersion) ─────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PlanRepositoryPort.class, PlanVersionRepositoryPort.class})
    public PlanApplicationService planApplicationService(
            PlanRepositoryPort planRepository, PlanVersionRepositoryPort planVersionRepository) {
        return new PlanApplicationServiceImpl(planRepository, planVersionRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PlanVersionRepositoryPort.class, PlanRepositoryPort.class})
    public PlanVersionApplicationService planVersionApplicationService(
            PlanVersionRepositoryPort planVersionRepository, PlanRepositoryPort planRepository) {
        return new PlanVersionApplicationServiceImpl(planVersionRepository, planRepository);
    }

    // ── Module ────────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ModuleRepositoryPort.class)
    public ModuleApplicationService moduleApplicationService(ModuleRepositoryPort moduleRepository) {
        return new ModuleApplicationServiceImpl(moduleRepository);
    }

    // ── AddOn ─────────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AddOnRepositoryPort.class)
    public AddOnApplicationService addOnApplicationService(AddOnRepositoryPort addOnRepository) {
        return new AddOnApplicationServiceImpl(addOnRepository);
    }

    // ── Entitlement ───────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(EntitlementRepositoryPort.class)
    public EntitlementApplicationService entitlementApplicationService(
            EntitlementRepositoryPort entitlementRepository) {
        return new EntitlementApplicationServiceImpl(entitlementRepository);
    }

    // ── PromoCode ─────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(PromoCodeRepositoryPort.class)
    public PromoCodeApplicationService promoCodeApplicationService(PromoCodeRepositoryPort promoCodeRepository) {
        return new PromoCodeApplicationServiceImpl(promoCodeRepository);
    }

    // ── PlanAddOn ─────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PlanRepositoryPort.class, AddOnRepositoryPort.class, PlanAddOnRepositoryPort.class})
    public PlanAddOnApplicationService planAddOnApplicationService(
            PlanRepositoryPort planRepository, AddOnRepositoryPort addOnRepository,
            PlanAddOnRepositoryPort planAddOnRepository) {
        return new PlanAddOnApplicationServiceImpl(planRepository, addOnRepository, planAddOnRepository);
    }

    // ── PlanModule ────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PlanRepositoryPort.class, ModuleRepositoryPort.class, PlanModuleRepositoryPort.class})
    public PlanModuleApplicationService planModuleApplicationService(
            PlanRepositoryPort planRepository, ModuleRepositoryPort moduleRepository,
            PlanModuleRepositoryPort planModuleRepository) {
        return new PlanModuleApplicationServiceImpl(planRepository, moduleRepository, planModuleRepository);
    }

    // ── PlanEntitlement (+ EntitlementResolver) ──────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PlanRepositoryPort.class, PlanEntitlementRepositoryPort.class, EntitlementRepositoryPort.class})
    public EntitlementResolver entitlementResolver(
            PlanRepositoryPort planRepository, PlanEntitlementRepositoryPort planEntitlementRepository,
            EntitlementRepositoryPort entitlementRepository) {
        return new DefaultEntitlementResolver(planRepository, planEntitlementRepository, entitlementRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PlanRepositoryPort.class, EntitlementRepositoryPort.class,
        PlanEntitlementRepositoryPort.class, EntitlementResolver.class})
    public PlanEntitlementApplicationService planEntitlementApplicationService(
            PlanRepositoryPort planRepository, EntitlementRepositoryPort entitlementRepository,
            PlanEntitlementRepositoryPort planEntitlementRepository, EntitlementResolver entitlementResolver) {
        return new PlanEntitlementApplicationServiceImpl(
            planRepository, entitlementRepository, planEntitlementRepository, entitlementResolver);
    }

    // ── PromoCodePlan ─────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PromoCodeRepositoryPort.class, PlanRepositoryPort.class, PromoCodePlanRepositoryPort.class})
    public PromoCodePlanApplicationService promoCodePlanApplicationService(
            PromoCodeRepositoryPort promoCodeRepository, PlanRepositoryPort planRepository,
            PromoCodePlanRepositoryPort promoCodePlanRepository) {
        return new PromoCodePlanApplicationServiceImpl(promoCodeRepository, planRepository, promoCodePlanRepository);
    }

    // ── PromoValidationService ────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PromoCodeRepositoryPort.class, PromoCodePlanRepositoryPort.class, PlanRepositoryPort.class})
    public PromoValidationService promoValidationService(
            PromoCodeRepositoryPort promoCodeRepository, PromoCodePlanRepositoryPort promoCodePlanRepository,
            PlanRepositoryPort planRepository) {
        return new PromoValidationServiceImpl(promoCodeRepository, promoCodePlanRepository, planRepository);
    }

    // ── CatalogQueryService ───────────────────────────────────────────────────
    // Opt-in via ppm.catalog.enabled=true (default false) — see PpmProperties
    // for why: this composite service's six-port, five-aggregate dependency
    // footprint is exactly the surprise ppm-demo's Phase 5 validation found
    // when scanning for it as if it were an ordinary part of the Plan aggregate.

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "ppm.catalog", name = "enabled", havingValue = "true")
    @ConditionalOnBean({PlanRepositoryPort.class, PlanVersionRepositoryPort.class, PlanModuleRepositoryPort.class,
        ModuleRepositoryPort.class, PlanEntitlementRepositoryPort.class, EntitlementRepositoryPort.class})
    public CatalogQueryService catalogQueryService(
            PlanRepositoryPort planRepository, PlanVersionRepositoryPort planVersionRepository,
            PlanModuleRepositoryPort planModuleRepository, ModuleRepositoryPort moduleRepository,
            PlanEntitlementRepositoryPort planEntitlementRepository, EntitlementRepositoryPort entitlementRepository) {
        return new CatalogQueryServiceImpl(planRepository, planVersionRepository, planModuleRepository,
            moduleRepository, planEntitlementRepository, entitlementRepository);
    }

    // ── PlanPrice (+ PricingResolver) ─────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PlanPriceRepositoryPort.class, PlanRepositoryPort.class})
    public PlanPriceApplicationService planPriceApplicationService(
            PlanPriceRepositoryPort planPriceRepository, PlanRepositoryPort planRepository) {
        return new PlanPriceApplicationServiceImpl(planPriceRepository, planRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({PlanRepositoryPort.class, PlanPriceRepositoryPort.class})
    public PricingResolver pricingResolver(
            PlanRepositoryPort planRepository, PlanPriceRepositoryPort planPriceRepository) {
        return new PricingResolverImpl(planRepository, planPriceRepository);
    }

    // ── AddOnPrice ────────────────────────────────────────────────────────────

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(AddOnPriceRepositoryPort.class)
    public AddOnPriceApplicationService addOnPriceApplicationService(
            AddOnPriceRepositoryPort addOnPriceRepository) {
        return new AddOnPriceApplicationServiceImpl(addOnPriceRepository);
    }
}
