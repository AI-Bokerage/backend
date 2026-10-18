# Dependency Audit

Every dependency declared in `ppm-core/build.gradle`, why it's there, and whether it's part of the library's public API surface (i.e. a consumer's classpath is affected by it) or a pure implementation detail.

## Production dependencies

| Dependency | Scope | Why it exists | API or implementation detail? |
|---|---|---|---|
| `org.springframework:spring-context` | `api` | `@Service` annotation on every use-case impl, so a host's component scan picks them up as beans. | **API-adjacent.** A consumer must be a Spring application (or provide equivalent bean registration) to use `ppm-core` at all — this is a real, accepted coupling, not incidental. |
| `org.springframework:spring-tx` | `api` | `@Transactional` / `@Transactional(readOnly = true)` on use-case methods that need transaction demarcation around multi-step repository calls (e.g. `PlanEntitlementApplicationServiceImpl.replaceEntitlements`'s delete-then-insert). | **API-adjacent.** The host's transaction manager (JPA, JDBC, whatever) must be active for these annotations to do anything; `ppm-core` declares the intent, the host supplies the mechanism. |
| `org.slf4j:slf4j-api` | `api` | Every use-case impl logs at `debug`/`warn`/`info` for business-rule outcomes (e.g. "plan.not_found id={}"). | **Implementation detail**, but declared `api` (not `implementation`) so a consumer can bind whatever SLF4J backend it already uses without a second logging facade appearing on the classpath. |
| `com.fasterxml.jackson.core:jackson-annotations` | `api` | `@JsonValue`/`@JsonCreator` on wire-vocabulary enums: `PlanVisibility`, `ModuleCode`, `DiscountType`, `PromoValidationReason`. Flagged, not silently accepted — see the Jackson note in [ADR-002](../../ppm-svc/docs/adr/ADR-002-ppm-core-extraction-pattern.md). | **API.** These are genuinely part of the enum's public contract (the wire value *is* domain vocabulary — "public"/"private"/"legacy" — not a transport detail). A host using a non-Jackson serialization stack would need to handle this; that porting cost is accepted for now and revisited only if it materializes. Only `jackson-annotations` (the annotation-only artifact) is pulled in — not `jackson-databind` or any Jackson module that would imply a specific serialization runtime. |
| `org.projectlombok:lombok` | `compileOnly` + `annotationProcessor` | `@Getter`, `@Builder`, `@RequiredArgsConstructor` throughout domain models and use-case impls — reduces boilerplate on ~60 files. | **Not on the consumer's runtime or compile classpath at all** (`compileOnly`) — Lombok is a compile-time-only annotation processor for `ppm-core` itself; a consumer never sees a Lombok dependency transitively. |

## Test dependencies (not part of the published artifact)

| Dependency | Why it exists |
|---|---|
| JUnit 5 (via `junit-bom:5.11.4`) | Test runner for all use-case unit tests. |
| AssertJ 3.26.3 | Fluent assertions used throughout the test suite. |
| Mockito 5.14.2 + `mockito-junit-jupiter` | Mocks every `*RepositoryPort` in use-case unit tests — no real persistence, no Testcontainers inside `ppm-core` (that's `ppm-svc`'s job, since only the host has a database). |

## Version alignment

`ppm-core` imports the `org.springframework.boot:spring-boot-dependencies:4.0.6` BOM via `io.spring.dependency-management`, purely to pin transitive versions of `spring-context`/`spring-tx` consistently with whatever Spring Boot version the reference host (`ppm-svc`) uses. `ppm-core` itself does **not** apply the Spring Boot Gradle plugin — there is no fat-jar/bootRun capability here, by design. `ppm-spring-boot-starter` (a separate module, see its README) is where Spring Boot auto-configuration lives; `ppm-core` stays framework-agnostic beyond bare Spring Framework wiring.

## What's deliberately absent

| Category | Example | Why absent |
|---|---|---|
| Web framework | Spring MVC, Spring WebFlux | Use cases return domain models; HTTP is entirely the host's concern. |
| Security | Spring Security | Actor identity is passed in as `UUID actorId` — see [ARCHITECTURE.md](../ARCHITECTURE.md#why-the-actor-identity-boundary-matters). |
| Persistence | JPA/Hibernate, Spring Data | Ports are interfaces only; the host chooses and implements the persistence technology. |
| Messaging | Spring AMQP, Kafka clients | `DomainEvent`s are collected on `BaseEntity` and drained via `pullDomainEvents()`; publishing them anywhere is a host decision. |
| Bean Validation | Jakarta Validation (`@NotNull`, `@NotBlank`) | Use-case methods validate business rules in code (e.g. "code must be unique"); input-shape validation (`@NotBlank` on a request field) is a host-DTO concern, validated before the use case is ever called. |
| Jackson databind | `jackson-databind`, `jackson-datatype-jsr310` | Only annotation types are needed (see above) — no actual (de)serialization happens inside `ppm-core`. |

## Coupling-reduction opportunities considered

- **`slf4j-api` as `api` vs. `implementation`:** kept as `api` deliberately so consumers aren't forced into a specific SLF4J binding by `ppm-core`; this is the standard pattern for library logging and is not considered excess coupling.
- **`jackson-annotations`:** the one dependency that is a "soft" library-design compromise rather than a clean requirement — see the ADR-002 note. No action taken this pass; flagged for revisit only if a non-Jackson host materializes.
- Everything else in the "deliberately absent" table above was evaluated during each aggregate's migration (per the ADR-002 checklist's "Decouple" step) and confirmed absent — this audit did not find any accidental leakage of a host-framework dependency into `ppm-core`'s `build.gradle`.
