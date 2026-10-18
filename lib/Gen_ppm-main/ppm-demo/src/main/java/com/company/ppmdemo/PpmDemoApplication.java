package com.company.ppmdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Independent second consumer of {@code ppm-core} — see README.md at the
 * repository root of this module for what this application does and does
 * not prove.
 *
 * <p>This app's own code lives entirely under {@code com.company.ppmdemo} —
 * a deliberately different base package from the library, to prove ppm-core
 * does not require a consumer to share its package namespace.
 *
 * <p><strong>Scan scope — a real Phase 5 finding.</strong> ppm-core's
 * {@code @Service} use-case beans all live under the single base package
 * {@code com.company.ppmsvc}, with no per-aggregate scan boundary. Scanning
 * that whole package (as a first attempt at this app did) instantiates
 * every use-case bean across all thirteen aggregates — including ones this
 * demo never calls (Entitlement, AddOn, PromoCodePlan, pricing, ...) —
 * which then fail to start unless the consumer implements every one of
 * their repository ports too, whether or not it needs them. Scanning only
 * the specific aggregate sub-packages this demo actually uses avoids that:
 * a consumer who wants a subset of ppm-core's aggregates has to know to
 * scope its component scan deliberately. This is noted as a documentation
 * gap in the Phase 5 report — INTEGRATION_GUIDE.md should say this
 * explicitly — not a code change to ppm-core itself.
 *
 * <p>Second finding compounding the first: {@code CatalogQueryServiceImpl}
 * co-locates in the {@code plan} package (per PACKAGE_GUIDE.md, since a
 * catalog listing is "fundamentally about plans") but its constructor pulls
 * in {@code ModuleRepositoryPort}, {@code EntitlementRepositoryPort},
 * {@code PlanModuleRepositoryPort}, and {@code PlanEntitlementRepositoryPort}
 * — four ports beyond Plan's own. Scanning {@code com.company.ppmsvc.plan}
 * for {@code PlanApplicationService} therefore also drags in a composite
 * service with a much larger dependency footprint than the package name
 * suggests. Excluded explicitly below since this demo has no catalog
 * listing endpoint — the same exclusion filter a real consumer would reach
 * for. This is a real consumer-experience cost of aggregate/composite
 * co-location that PACKAGE_GUIDE.md does not currently call out.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.company.ppmdemo",
    "com.company.ppmsvc.plan",
    "com.company.ppmsvc.module",
    "com.company.ppmsvc.promocode",
    "com.company.ppmsvc.planmodule"
}, excludeFilters = @ComponentScan.Filter(
    type = FilterType.REGEX,
    pattern = "com\\.company\\.ppmsvc\\.plan\\.usecase\\.CatalogQueryService.*"
))
public class PpmDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(PpmDemoApplication.class, args);
    }
}
