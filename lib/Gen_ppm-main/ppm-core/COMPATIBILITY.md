# Compatibility

What `ppm-core` and `ppm-spring-boot-starter` require and support, as of `0.0.1-SNAPSHOT`. For the policy on how these requirements may change release to release, see `VERSIONING.md`.

## Java

| Component | Requirement |
|---|---|
| `ppm-core` | Java 21 (Gradle toolchain-pinned) |
| `ppm-spring-boot-starter` | Java 21 |

No lower-version support is offered — both modules use language features (records, pattern-matching `instanceof`, sealed-adjacent patterns where relevant) that assume 21.

## Spring

| Component | Requirement |
|---|---|
| `ppm-core` | `spring-context` + `spring-tx` 7.0.7 (aligned to the `spring-boot-dependencies:4.0.6` BOM) |
| `ppm-spring-boot-starter` | Spring Boot 4.0.6 (`spring-boot-autoconfigure` 4.0.6) |

`ppm-core` itself does not require Spring Boot — only bare Spring Framework (`spring-context`/`spring-tx`) for `@Service`/`@Transactional`. A consumer could theoretically wire `ppm-core`'s use-case beans into a plain Spring Framework application without Spring Boot at all; `ppm-spring-boot-starter` is the officially supported path but not the only possible one.

## What is NOT supported

- Non-Spring dependency injection (Guice, Dagger, manual wiring without a DI container) — untested, though nothing in `ppm-core` structurally forbids it since use-case classes are plain constructor-injected POJOs underneath the `@Service` annotation.
- Non-Jackson serialization stacks — see `docs/DEPENDENCY_AUDIT.md` for the one accepted Jackson coupling (`@JsonValue`/`@JsonCreator` on wire-vocabulary enums).
- JPMS (the Java Platform Module System) — no `module-info.java` is provided. This is a deliberate omission, not an oversight: adopting JPMS is a strategic decision with real consequences for reflection-based frameworks (Spring, Jackson, Lombok's annotation processing) and has not been evaluated against a real requirement. Revisit only if a concrete need for strong module boundaries at the JVM level materializes.

## Version alignment policy

`ppm-core` and `ppm-spring-boot-starter` are released together, at the same version, because the starter has a hard compile-time dependency on the exact `ppm-core` version it was built against (see `RELEASE_PROCESS.md`). There is currently no support matrix for "starter version N with core version M ≠ N" — assume they must match.
