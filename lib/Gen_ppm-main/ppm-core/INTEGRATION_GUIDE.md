# Integration Guide

How to consume `ppm-core` from a Spring Boot application. `ppm-svc` (this repository) is the reference host — every step below points at the actual file that implements it there.

## 1. Add the dependency

`ppm-core` is not yet published to a Maven repository (see [VERSIONING.md](VERSIONING.md)). Within this repository's Gradle multi-module build:

```groovy
// settings.gradle
include 'ppm-core'
include 'your-service'
```

```groovy
// your-service/build.gradle
dependencies {
    implementation project(':ppm-core')
}
```

Once versioned releases begin, this becomes a normal coordinate: `implementation 'com.company:ppm-core:<version>'`.

## 2. Implement the outbound repository ports

For every aggregate you need, implement its `*RepositoryPort` interface. The port speaks only in domain models — you decide the persistence technology.

```java
// your-service's persistence adapter
@Repository
@RequiredArgsConstructor
public class JpaPlanRepositoryAdapter implements PlanRepositoryPort {

    private final PlanJpaRepository jpaRepository; // your Spring Data repository
    private final PlanEntityMapper  entityMapper;  // your JPA-entity ↔ domain mapper

    @Override
    public Optional<Plan> findById(UUID id) {
        return jpaRepository.findById(id).map(entityMapper::toDomain);
    }

    @Override
    public Plan save(Plan plan) {
        return entityMapper.toDomain(jpaRepository.save(entityMapper.toEntity(plan)));
    }

    // ... remaining port methods
}
```

`ppm-svc`'s adapters live under `ppm-svc/src/main/java/com/company/ppmsvc/infrastructure/persistence/` — use them as a reference for the JPA-entity/domain split, but note that `ppm-core` does not mandate JPA; any persistence technology that can satisfy the port interface works.

## 3. Register the use-case beans

Every `<Aggregate>ApplicationServiceImpl` is already annotated `@Service` and `@RequiredArgsConstructor`, so a standard Spring component scan over `com.company.ppmsvc` picks them up automatically as long as your host's `@SpringBootApplication` (or an explicit `@ComponentScan`) covers that base package.

**Prefer [`ppm-spring-boot-starter`](../ppm-spring-boot-starter) over manual component scanning if you're on Spring Boot.** The starter automates exactly this step — registering one use-case bean per aggregate, conditionally on that aggregate's ports being present, with a consumer's own bean always taking precedence — and additionally validates your port wiring at startup (see its README). The manual approach below remains valid for non-Spring-Boot Spring applications, or if you want full manual control.

**Scope your component scan deliberately if you only need a subset of aggregates.** `ppm-core` has a single flat base package (`com.company.ppmsvc`) with no per-aggregate scan boundary — there is no `spring.factories`-style mechanism to opt into "just Plan" or "just PromoCode." Scanning the whole base package instantiates every use-case bean across **all** aggregates, and Spring will fail to start unless every one of their repository ports has an implementation bean — including aggregates you never call. `ppm-demo` (see its README) hit this directly and works around it by scanning only the aggregate sub-packages it needs:

```java
@ComponentScan(basePackages = {
    "com.yourhost.yourapp",
    "com.company.ppmsvc.plan",
    "com.company.ppmsvc.module",
    "com.company.ppmsvc.promocode",
})
```

**Watch for composite services when scanning by sub-package.** Some use-case classes co-locate in an aggregate's package for design reasons (see `PACKAGE_GUIDE.md`) but depend on other aggregates' ports — `CatalogQueryServiceImpl`, for example, lives in `plan` but requires `ModuleRepositoryPort`, `EntitlementRepositoryPort`, `PlanModuleRepositoryPort`, and `PlanEntitlementRepositoryPort`. Scanning `com.company.ppmsvc.plan` for `PlanApplicationService` alone will also pull this in. If you don't need it, exclude it explicitly:

```java
@ComponentScan(basePackages = {...},
    excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
        pattern = "com\\.company\\.ppmsvc\\.plan\\.usecase\\.CatalogQueryService.*"))
```

Check [docs/API_INVENTORY.md](docs/API_INVENTORY.md) for each aggregate's full port list before deciding what to scan and what to exclude.

## 4. Wire the controller: actor identity + DTO mapping

This is the one step every host must implement itself — `ppm-core` use cases never resolve the caller's identity or accept host DTOs directly.

```java
@RestController
@RequestMapping("/api/v1/ppm/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanApplicationService planService; // from ppm-core
    private final PlanApiMapper          planApiMapper; // your own MapStruct mapper, host-side

    @PostMapping
    public ResponseEntity<ApiResponse<PlanResponse>> create(@Valid @RequestBody CreatePlanRequest request) {
        UUID actorId = SecurityUtils.requireCurrentUserId(); // however YOUR host resolves identity
        Plan saved = planService.createPlan(actorId, request.code(), request.name(), /* ... */);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.created("Plan created.", planApiMapper.toResponse(saved)));
    }
}
```

See `ppm-svc/src/main/java/com/company/ppmsvc/api/controller/PlanController.java` for the complete, tested reference implementation of this pattern, and `ppm-svc/src/main/java/com/company/ppmsvc/api/mapper/PlanApiMapper.java` for the MapStruct mapper it uses.

## 5. Map domain exceptions to your transport

Every exception `ppm-core` throws extends `BusinessException` and carries an `ErrorCode`. Map exception *type* (not code) to your transport's error representation:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    // AccessDeniedException -> 403, InvalidStateException -> 409, BusinessException -> 400, etc.
}
```

## What stays in the host — checklist

- [ ] REST controllers, request/response DTOs, OpenAPI annotations
- [ ] MapStruct (or hand-written) DTO ↔ domain mapping
- [ ] Actor-identity resolution (`SecurityContextHolder`, JWT claims, whatever your auth stack is)
- [ ] JPA entities, Spring Data repositories, port-implementing adapter classes
- [ ] Messaging (publishing domain events drained via `entity.pullDomainEvents()`)
- [ ] Global exception → HTTP status mapping
- [ ] All Spring Boot auto-configuration, `application.yaml`, actuator, security filter chains

## What `ppm-core` guarantees in return

- Business rules (uniqueness checks, state-transition validation, discount/entitlement resolution logic) are implemented once and tested once — every host gets the same correctness guarantees.
- Use-case signatures are stable across the domain-model boundary — changing your DTO shape never requires touching `ppm-core`.
- No transitive Spring MVC/Security/JPA/AMQP dependency is pulled into your classpath by depending on `ppm-core` — see [docs/DEPENDENCY_AUDIT.md](docs/DEPENDENCY_AUDIT.md).

For the full public-API surface per aggregate (which methods, which exceptions, which host responsibilities), see [docs/API_INVENTORY.md](docs/API_INVENTORY.md).
