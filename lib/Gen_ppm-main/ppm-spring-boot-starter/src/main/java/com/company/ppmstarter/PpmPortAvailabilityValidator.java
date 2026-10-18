package com.company.ppmstarter;

import com.company.ppmsvc.addon.port.AddOnRepositoryPort;
import com.company.ppmsvc.addonprice.port.AddOnPriceRepositoryPort;
import com.company.ppmsvc.entitlement.port.EntitlementRepositoryPort;
import com.company.ppmsvc.module.port.ModuleRepositoryPort;
import com.company.ppmsvc.plan.port.PlanRepositoryPort;
import com.company.ppmsvc.plan.port.PlanVersionRepositoryPort;
import com.company.ppmsvc.planaddon.port.PlanAddOnRepositoryPort;
import com.company.ppmsvc.planentitlement.port.PlanEntitlementRepositoryPort;
import com.company.ppmsvc.planmodule.port.PlanModuleRepositoryPort;
import com.company.ppmsvc.planprice.port.PlanPriceRepositoryPort;
import com.company.ppmsvc.promocode.port.PromoCodeRepositoryPort;
import com.company.ppmsvc.promocodeplan.port.PromoCodePlanRepositoryPort;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

/**
 * Fails startup fast, with a specific message, when a consumer has
 * implemented some — but not all — of the repository ports one of
 * ppm-core's aggregates requires.
 *
 * <p>Runs as a {@link SmartInitializingSingleton}: after every singleton in
 * the context (including every consumer-supplied port adapter) has been
 * instantiated, but before the application is considered started. This is
 * deliberately earlier than the first time some unrelated bean tries to
 * {@code @Autowired} the missing use-case service and gets a generic
 * {@code NoSuchBeanDefinitionException} with no hint about *why*.
 *
 * <p>An aggregate whose <strong>anchor</strong> port (see
 * {@link AggregatePortRequirement}) is absent is not an error — that's a
 * consumer who doesn't want that aggregate, and
 * {@link PpmUseCaseAutoConfiguration} silently skips registering its
 * use-case bean (see {@code ppm-demo}, which implements only six of
 * ppm-core's twelve ports by design). Composites with no port unique to
 * themselves — {@code CatalogQueryService}, {@code PromoValidationService},
 * both built entirely from other independently-adoptable aggregates' ports —
 * are intentionally not modeled here at all: there is no way to distinguish
 * "partially wired composite" from "these other aggregates were adopted
 * independently and just happen to overlap," so attempting to validate them
 * here would produce false positives rather than catching real mistakes.
 */
public class PpmPortAvailabilityValidator implements SmartInitializingSingleton {

    private static final List<AggregatePortRequirement> REQUIREMENTS = List.of(
        new AggregatePortRequirement("Plan (+ PlanVersion)", PlanVersionRepositoryPort.class,
            List.of(PlanRepositoryPort.class, PlanVersionRepositoryPort.class)),
        new AggregatePortRequirement("Module", ModuleRepositoryPort.class,
            List.of(ModuleRepositoryPort.class)),
        new AggregatePortRequirement("AddOn", AddOnRepositoryPort.class,
            List.of(AddOnRepositoryPort.class)),
        new AggregatePortRequirement("Entitlement", EntitlementRepositoryPort.class,
            List.of(EntitlementRepositoryPort.class)),
        new AggregatePortRequirement("PromoCode", PromoCodeRepositoryPort.class,
            List.of(PromoCodeRepositoryPort.class)),
        new AggregatePortRequirement("PlanAddOn", PlanAddOnRepositoryPort.class,
            List.of(PlanRepositoryPort.class, AddOnRepositoryPort.class, PlanAddOnRepositoryPort.class)),
        new AggregatePortRequirement("PlanModule", PlanModuleRepositoryPort.class,
            List.of(PlanRepositoryPort.class, ModuleRepositoryPort.class, PlanModuleRepositoryPort.class)),
        new AggregatePortRequirement("PlanEntitlement (+ EntitlementResolver)", PlanEntitlementRepositoryPort.class,
            List.of(PlanRepositoryPort.class, EntitlementRepositoryPort.class, PlanEntitlementRepositoryPort.class)),
        new AggregatePortRequirement("PromoCodePlan", PromoCodePlanRepositoryPort.class,
            List.of(PromoCodeRepositoryPort.class, PlanRepositoryPort.class, PromoCodePlanRepositoryPort.class)),
        // CatalogQueryService and PromoValidationService deliberately excluded — see class Javadoc.
        new AggregatePortRequirement("PlanPrice (+ PricingResolver)", PlanPriceRepositoryPort.class,
            List.of(PlanPriceRepositoryPort.class, PlanRepositoryPort.class)),
        new AggregatePortRequirement("AddOnPrice", AddOnPriceRepositoryPort.class,
            List.of(AddOnPriceRepositoryPort.class))
    );

    private static final List<Class<?>> ALL_PORT_TYPES = REQUIREMENTS.stream()
        .flatMap(r -> r.requiredPorts().stream())
        .distinct()
        .toList();

    private final ConfigurableListableBeanFactory beanFactory;

    public PpmPortAvailabilityValidator(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<Class<?>> available = new HashSet<>();
        for (Class<?> portType : ALL_PORT_TYPES) {
            if (beanFactory.getBeanNamesForType(portType).length > 0) {
                available.add(portType);
            }
        }

        List<String> violations = REQUIREMENTS.stream()
            .filter(r -> r.anchorPresent(available) && !r.missingFrom(available).isEmpty())
            .map(r -> describe(r, available))
            .collect(Collectors.toList());

        if (!violations.isEmpty()) {
            throw new PpmMissingRepositoryPortException(
                "ppm-core aggregate(s) partially wired — a repository port implementation is "
                    + "missing for at least one bean that would otherwise be created:\n"
                    + String.join("\n", violations)
                    + "\n\nEither implement and register the missing port(s) as Spring beans, "
                    + "or remove the beans for the other port(s) in the same group if you did not "
                    + "intend to use this aggregate.");
        }
    }

    private String describe(AggregatePortRequirement requirement, Set<Class<?>> available) {
        List<String> missing = requirement.missingFrom(available).stream().map(Class::getSimpleName).toList();
        List<String> present = requirement.requiredPorts().stream()
            .filter(available::contains).map(Class::getSimpleName).toList();
        return "  - %s: present %s, missing %s".formatted(requirement.aggregateName(), present, missing);
    }
}
