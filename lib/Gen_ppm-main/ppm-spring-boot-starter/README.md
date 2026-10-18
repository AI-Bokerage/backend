# ppm-spring-boot-starter

The official Spring Boot integration layer for [`ppm-core`](../ppm-core). Eliminates manual `@Bean` wiring for every use-case service, validates that a consumer's repository-port implementations are complete at startup (not later, as an unrelated `NoSuchBeanDefinitionException`), and exposes one small, documented configuration namespace.

**Contains no business logic.** Every business rule, validation, and domain decision lives in `ppm-core`; this module only registers beans and checks that registration is complete.

## What this module owns — and doesn't

Owns:
- Spring Boot auto-configuration (`PpmAutoConfiguration`, `PpmUseCaseAutoConfiguration`)
- `@ConfigurationProperties` (`PpmProperties`)
- Startup repository-port validation (`PpmPortAvailabilityValidator`)

Never contains, and never will:
- Controllers, DTOs, or any REST-shaped type
- JPA entities, Flyway migrations, or any persistence adapter
- Security, RabbitMQ/Kafka, or any other host-infrastructure concern
- A dependency on `ppm-svc` or `ppm-demo` — this module's `build.gradle` depends only on `ppm-core` and `spring-boot-autoconfigure`

## Quick start

```groovy
// your app's build.gradle
dependencies {
    implementation project(':ppm-spring-boot-starter') // or the published coordinate, once released
}
```

```java
@Configuration
class MyPortAdapters {
    @Bean PlanRepositoryPort planRepositoryPort() { return new MyJpaPlanRepositoryAdapter(...); }
    @Bean PlanVersionRepositoryPort planVersionRepositoryPort() { return new MyJpaPlanVersionRepositoryAdapter(...); }
}
```

That's it — no `@Bean PlanApplicationService(...)` needed. The starter's auto-configuration sees both required ports and registers `PlanApplicationService` for you.

```java
@RestController
@RequiredArgsConstructor
class MyPlanController {
    private final PlanApplicationService planService; // wired automatically
}
```

## Auto-configured beans

One bean per aggregate, registered only when **all** of its required repository ports are present in the context (see the table below). Every bean is `@ConditionalOnMissingBean` — define your own and it wins.

| Use-case bean | Required ports | Notes |
|---|---|---|
| `PlanApplicationService` | `PlanRepositoryPort`, `PlanVersionRepositoryPort` | |
| `PlanVersionApplicationService` | `PlanVersionRepositoryPort`, `PlanRepositoryPort` | |
| `ModuleApplicationService` | `ModuleRepositoryPort` | |
| `AddOnApplicationService` | `AddOnRepositoryPort` | |
| `EntitlementApplicationService` | `EntitlementRepositoryPort` | |
| `PromoCodeApplicationService` | `PromoCodeRepositoryPort` | |
| `PlanAddOnApplicationService` | `PlanRepositoryPort`, `AddOnRepositoryPort`, `PlanAddOnRepositoryPort` | |
| `PlanModuleApplicationService` | `PlanRepositoryPort`, `ModuleRepositoryPort`, `PlanModuleRepositoryPort` | |
| `EntitlementResolver` | `PlanRepositoryPort`, `PlanEntitlementRepositoryPort`, `EntitlementRepositoryPort` | Registered as `DefaultEntitlementResolver` |
| `PlanEntitlementApplicationService` | above three ports + `EntitlementResolver` bean | |
| `PromoCodePlanApplicationService` | `PromoCodeRepositoryPort`, `PlanRepositoryPort`, `PromoCodePlanRepositoryPort` | |
| `PromoValidationService` | `PromoCodeRepositoryPort`, `PromoCodePlanRepositoryPort`, `PlanRepositoryPort` | |
| `PlanPriceApplicationService` | `PlanPriceRepositoryPort`, `PlanRepositoryPort` | |
| `PricingResolver` | `PlanRepositoryPort`, `PlanPriceRepositoryPort` | |
| `AddOnPriceApplicationService` | `AddOnPriceRepositoryPort` | |
| `CatalogQueryService` | the above six ports **+** `ppm.catalog.enabled=true` | **Not** auto-registered by default — see below |

### Why `CatalogQueryService` needs an explicit property

`CatalogQueryService` composes five aggregates' ports (Plan, PlanVersion, PlanModule, Module, PlanEntitlement, Entitlement). `ppm-demo`'s Phase 5 validation found that scanning for the Plan aggregate alone pulls this composite service in unexpectedly, forcing five other ports into scope. Set `ppm.catalog.enabled=true` once you actually want it; leave it unset otherwise.

## Configuration properties

```yaml
ppm:
  enabled: true          # master switch — false disables the entire starter (default: true)
  catalog:
    enabled: false        # opt-in for CatalogQueryService (default: false — see above)
```

This is the one place starter configuration lives — see `PpmProperties`. Future options (event publishing, caching, strict-mode validation, etc.) belong here, not as scattered `@Value` lookups.

## Repository port validation

If you implement **some but not all** of an aggregate's required ports, the starter fails at context refresh with a message naming the aggregate, which ports are present, which are missing, and how to fix it:

```
ppm-core aggregate(s) partially wired — a repository port implementation is missing for at least one bean that would otherwise be created:
  - PlanModule: present [PlanRepositoryPort, ModuleRepositoryPort], missing [PlanModuleRepositoryPort]

Either implement and register the missing port(s) as Spring beans, or remove the beans for the other port(s) in the same group if you did not intend to use this aggregate.
```

This fires immediately at startup — not later as a generic `NoSuchBeanDefinitionException` when some unrelated bean tries to `@Autowired` the missing service.

**Important nuance:** implementing **zero** of an aggregate's ports is never an error — that's a consumer who doesn't want that aggregate, and its use-case bean is silently skipped. Only a *partial* implementation triggers the failure. Also note that a widely-shared port like `PlanRepositoryPort` alone never triggers this on its own — see `AggregatePortRequirement`'s Javadoc for why (in short: `PlanRepositoryPort` is required by nearly every aggregate, so its presence alone says nothing about intent toward any one of them; validation anchors on each aggregate's genuinely unique port instead).

## Bean overrides

Every auto-configured bean is `@ConditionalOnMissingBean`. Define your own `@Bean PlanApplicationService(...)` and the starter's default is never created — standard Spring Boot convention, no starter-specific opt-out mechanism needed.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `PpmMissingRepositoryPortException` at startup | You implemented some, not all, of an aggregate's required ports | Implement the missing port(s), or remove the other ports in that group if you don't want the aggregate |
| A use-case bean you expected isn't registered, no exception | You implemented zero of that aggregate's ports | This is by design — implement its required port(s) to enable it |
| `CatalogQueryService` isn't registered even with all six ports present | `ppm.catalog.enabled` is unset or `false` | Set `ppm.catalog.enabled=true` |
| Starter seems entirely inactive | `ppm.enabled=false` is set somewhere, or `ppm-core` isn't actually on the classpath | Check `ppm.enabled`; confirm `PlanApplicationService` resolves on your classpath |

## Testing this starter

`PpmAutoConfigurationTest` (using Spring Boot's `ApplicationContextRunner`) covers, per Phase 6 Work Package 6:

1. Successful startup with required ports present (three variants: full aggregate, single-port aggregate, zero-port aggregate silently skipped).
2. Clear failure with a specific message when ports are partially implemented — plus a regression test proving the widely-shared-port false positive (see above) does NOT fire.
3. Consumer-defined beans taking precedence over the starter's defaults.
4. Conditional activation: `ppm.enabled=false` disables everything; default-enabled with no property set; `CatalogQueryService`'s opt-in gate.
5. Documents (rather than re-proves) that this module has no `ppm-svc` dependency — enforced at compile time by `build.gradle`, not something a runtime test can meaningfully assert.

All ten tests pass. See [`ppm-core/docs/PHASE_5_READINESS_REPORT.md`](../ppm-core/docs/PHASE_5_READINESS_REPORT.md) for the broader consumer-validation context this starter was built on top of.
