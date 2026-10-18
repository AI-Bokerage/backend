# Gen_AUTH Starter Library Conversion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure Gen_AUTH from a single-module standalone service into a two-module Gradle build — `:gen-auth-starter` (a reusable Spring Boot auto-configuration library, core-auth-only scope) and `:gen-auth-demo` (a thin standalone app proving the starter works, keeping Gen_AUTH runnable directly).

**Architecture:** Every existing `@Service`/`@RestController`/`@Repository` class moves into `:gen-auth-starter` unchanged; a handful of new `@AutoConfiguration` glue classes (`@ComponentScan`/`@EntityScan`/`@EnableJpaRepositories`, registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`) make Spring find them from inside any host app regardless of that app's own base package. The starter runs its own independent Flyway instance (own history table, own classpath location, ordered before JPA validation via `EntityManagerFactoryDependsOnPostProcessor`) so it never depends on or collides with a host app's own migration tooling.

**Tech Stack:** Gradle multi-module, Spring Boot 4.0.6 (BOM-only in the starter, full plugin in the demo), Flyway (programmatic API, no Spring Boot Flyway autoconfiguration), GitHub Packages (Maven).

## Global Constraints

- v1 scope is core auth only: register, login, refresh (rotation+replay), logout, logout-all, change-password, session, internal user provisioning, JWKS serving + runtime multi-key rotation. Magic-link, super-admin/impersonation, RabbitMQ events, and email sending are removed from the starter for v1 (recoverable from git history later).
- The demo module's own application class must live in a **different Java package** than the starter's classes (`com.example.gendemo`, not `com.example.authsvc`) — otherwise Spring Boot's default component scan (rooted at the demo app's own package) would accidentally find the starter's classes on its own, masking whether the `@AutoConfiguration` mechanism actually works. A real client's app won't share a package with this codebase, so the demo must not either.
- The starter's Flyway migration must never be discoverable by a host app's own (potentially default-configured) Flyway autoconfiguration. Migration files live at `classpath:db/migration/genauth/` inside the starter jar (not the default `classpath:db/migration`), and the starter itself never depends on `spring-boot-starter-flyway` (only plain `flyway-core` + `flyway-database-postgresql`), so Spring Boot's own Flyway autoconfiguration bean never activates from the starter's presence on the classpath.
- Publish target is GitHub Packages, artifact coordinates `com.example:gen-auth-starter`. Actually publishing to the remote registry requires live `GITHUB_ACTOR`/`GITHUB_TOKEN` credentials this plan cannot provide — verification stops at `publishToMavenLocal`.

---

### Task 1: Multi-module Gradle skeleton

**Files:**
- Modify: `settings.gradle`
- Modify: `build.gradle` (root)
- Create: `gen-auth-starter/build.gradle`
- Create: `gen-auth-demo/build.gradle`

**Interfaces:**
- Produces: two Gradle subprojects, `:gen-auth-starter` (java-library) and `:gen-auth-demo` (Spring Boot app depending on the starter) — consumed by every later task, which adds files under `gen-auth-starter/src/...` or `gen-auth-demo/src/...`.

No source moves yet in this task — just the module skeleton, so the very next task's `git mv` has somewhere to land.

- [ ] **Step 1: Update settings.gradle**

```groovy
// settings.gradle — full file after this change
rootProject.name = 'gen-auth'
include(':gen-auth-starter', ':gen-auth-demo')
```

- [ ] **Step 2: Replace root build.gradle with shared coordinates only**

```groovy
// build.gradle — full file after this change
allprojects {
    group = 'com.example'
    version = '0.0.1-SNAPSHOT'

    repositories {
        mavenCentral()

        // Required for OpenSAML dependencies (transitively pulled by spring-security-saml2-service-provider)
        maven {
            url = "https://build.shibboleth.net/nexus/content/repositories/releases/"
        }
    }
}
```

- [ ] **Step 3: Create gen-auth-starter/build.gradle — full original dependency set, java-library instead of the boot plugin**

This is a straight copy of the current (pre-split) `build.gradle`'s dependency list — nothing is trimmed yet. Trimming (removing mail/amqp/ses) happens in Task 3, once the files that need them are actually deleted, so this task's diff stays purely structural.

```groovy
// gen-auth-starter/build.gradle
plugins {
    id 'java-library'
    id 'io.spring.dependency-management' version '1.1.7'
}

description = 'gen-auth-starter'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

ext {
    mapstructVersion      = '1.6.3'
    resilience4jVersion   = '2.4.0'
    archunitVersion       = '1.3.0'
    springdocVersion      = '3.0.3'
    jjwtVersion           = '0.12.7'
    bouncyCastleVersion   = '1.80'
    testcontainersVersion = '1.21.3'
    awsSdkVersion         = '2.27.21'
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.6"
        mavenBom "org.testcontainers:testcontainers-bom:${testcontainersVersion}"
    }
}

dependencies {

    // ─────────────────────────────────────────────────────────────────────
    // Web
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'

    // ─────────────────────────────────────────────────────────────────────
    // Email
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-mail'

    // ─────────────────────────────────────────────────────────────────────
    // Security
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'

    // Future SSO Support
    implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
    implementation 'org.springframework.security:spring-security-saml2-service-provider'

    // ─────────────────────────────────────────────────────────────────────
    // JWT
    // ─────────────────────────────────────────────────────────────────────
    implementation "io.jsonwebtoken:jjwt-api:${jjwtVersion}"
    runtimeOnly "io.jsonwebtoken:jjwt-impl:${jjwtVersion}"
    runtimeOnly "io.jsonwebtoken:jjwt-jackson:${jjwtVersion}"

    // ─────────────────────────────────────────────────────────────────────
    // BouncyCastle — required by Spring Security Argon2PasswordEncoder
    // ─────────────────────────────────────────────────────────────────────
    implementation "org.bouncycastle:bcprov-jdk18on:${bouncyCastleVersion}"

    // ─────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // ─────────────────────────────────────────────────────────────────────
    // AOP
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver'

    // ─────────────────────────────────────────────────────────────────────
    // Persistence
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'

    // ─────────────────────────────────────────────────────────────────────
    // Flyway — plain library, NOT spring-boot-starter-flyway. The starter
    // runs its own independent Flyway instance (see Task 4); it must never
    // pull in Spring Boot's own Flyway autoconfiguration, which would try
    // to run against the host app's default classpath:db/migration location.
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.flywaydb:flyway-core'
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'

    // ─────────────────────────────────────────────────────────────────────
    // Valkey
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.session:spring-session-data-redis'
    implementation 'io.lettuce:lettuce-core'
    implementation 'org.apache.commons:commons-pool2'

    // ─────────────────────────────────────────────────────────────────────
    // Cache
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-cache'
    implementation 'com.github.ben-manes.caffeine:caffeine'

    // ─────────────────────────────────────────────────────────────────────
    // RabbitMQ Messaging
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-amqp'

    // ─────────────────────────────────────────────────────────────────────
    // Observability
    // ─────────────────────────────────────────────────────────────────────
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-tracing-bridge-otel'
    implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
    runtimeOnly 'io.micrometer:micrometer-registry-prometheus'

    // ─────────────────────────────────────────────────────────────────────
    // Resilience4j
    // ─────────────────────────────────────────────────────────────────────
    implementation "io.github.resilience4j:resilience4j-spring-boot4:${resilience4jVersion}"
    implementation "io.github.resilience4j:resilience4j-micrometer:${resilience4jVersion}"

    // ─────────────────────────────────────────────────────────────────────
    // Swagger / OpenAPI
    // ─────────────────────────────────────────────────────────────────────
    implementation "org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdocVersion}"

    // ─────────────────────────────────────────────────────────────────────
    // Mapping
    // ─────────────────────────────────────────────────────────────────────
    implementation "org.mapstruct:mapstruct:${mapstructVersion}"
    annotationProcessor "org.mapstruct:mapstruct-processor:${mapstructVersion}"
    annotationProcessor 'org.projectlombok:lombok-mapstruct-binding:0.2.0'

    // ─────────────────────────────────────────────────────────────────────
    // Lombok
    // ─────────────────────────────────────────────────────────────────────
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // ─────────────────────────────────────────────────────────────────────
    // Configuration Processor
    // ─────────────────────────────────────────────────────────────────────
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    // ─────────────────────────────────────────────────────────────────────
    // AWS SDK v2 — KMS asymmetric signing (jwt.signing-mode=kms)
    //            — SES email delivery (mail.provider=ses)
    // ─────────────────────────────────────────────────────────────────────
    implementation platform("software.amazon.awssdk:bom:${awsSdkVersion}")
    implementation 'software.amazon.awssdk:kms'
    implementation 'software.amazon.awssdk:ses'

    // ─────────────────────────────────────────────────────────────────────
    // Testing
    // ─────────────────────────────────────────────────────────────────────
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:rabbitmq'
    testImplementation 'org.springframework.amqp:spring-rabbit-test'
    testImplementation "com.tngtech.archunit:archunit-junit5:${archunitVersion}"
    testImplementation 'org.awaitility:awaitility'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Create gen-auth-demo/build.gradle**

```groovy
// gen-auth-demo/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
}

description = 'gen-auth-demo'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation project(':gen-auth-starter')
    // Needed on this module's own compile classpath for @SpringApplication/
    // @SpringBootApplication — gen-auth-starter declares its Spring Boot
    // dependencies as `implementation`, which java-library does not expose
    // transitively to a consumer's compile classpath (only `api` deps are).
    implementation 'org.springframework.boot:spring-boot-starter'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
}

tasks.named('bootRun') {
    def envFile = file("${projectDir}/.env")
    if (envFile.exists()) {
        envFile.readLines().each { line ->
            def trimmed = line.trim()
            if (trimmed && !trimmed.startsWith('#')) {
                def idx = trimmed.indexOf('=')
                if (idx > 0) {
                    environment(trimmed.substring(0, idx).trim(), trimmed.substring(idx + 1).trim())
                }
            }
        }
    }
}
```

- [ ] **Step 5: Create empty placeholder source directories so Gradle recognizes both modules**

```bash
mkdir -p gen-auth-starter/src/main/java gen-auth-starter/src/test/java
mkdir -p gen-auth-demo/src/main/java gen-auth-demo/src/test/java
```

- [ ] **Step 6: Verify the module skeleton is recognized**

Run: `./gradlew.bat projects`
Expected: output lists both `Project ':gen-auth-starter'` and `Project ':gen-auth-demo'`

Run: `./gradlew.bat build`
Expected: `BUILD SUCCESSFUL` (both modules compile — trivially, since neither has source yet)

- [ ] **Step 7: Commit**

```bash
git add settings.gradle build.gradle gen-auth-starter/build.gradle gen-auth-demo/build.gradle
git commit -m "add multi-module Gradle skeleton: gen-auth-starter + gen-auth-demo"
```

---

### Task 2: Mechanical relocation — move all existing source into gen-auth-starter

**Files:**
- Move (whole directories, `git mv`): `src/main/java` → `gen-auth-starter/src/main/java`, `src/test/java` → `gen-auth-starter/src/test/java`, `src/main/resources` → `gen-auth-starter/src/main/resources`, `src/test/resources` → `gen-auth-starter/src/test/resources`

**Interfaces:**
- Produces: every existing class, unchanged, now living under `gen-auth-starter/`. No behavior change — this task's diff should be 100% file renames, zero content edits. Task 3 will delete the out-of-scope subset; Task 6 will move `AuthSvcApplication` again into the demo module.

This is intentionally a pure structural move so it's trivially reviewable — confirm nothing was left behind and everything still compiles exactly as before, with zero logic changes to verify.

- [ ] **Step 1: Move the four top-level source/resource trees**

```bash
git mv src/main/java gen-auth-starter/src/main/java
git mv src/test/java gen-auth-starter/src/test/java
git mv src/main/resources gen-auth-starter/src/main/resources
git mv src/test/resources gen-auth-starter/src/test/resources
```

- [ ] **Step 2: Confirm nothing else remains in the old src/ tree**

Run: `find src -type f 2>&1`
Expected: `find: 'src': No such file or directory` (the whole directory moved, nothing left behind)

If anything unexpected remains (e.g., a file this plan didn't account for), STOP and report it — don't silently leave it behind or silently move it without understanding what it is.

- [ ] **Step 3: Verify the relocation compiles and tests pass exactly as before**

Run: `./gradlew.bat :gen-auth-starter:test`
Expected: `BUILD SUCCESSFUL`, same test count as before the move (the JWKS rotation slice left this suite at 38 tests, 0 failures, 1 skipped — the Testcontainers-gated integration test, which skips in this sandbox because Docker Engine API access is blocked here even though the `docker` CLI works)

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "relocate all existing source into gen-auth-starter (pure move, no content changes)"
```

---

### Task 3: Trim to core-auth-only scope

**Files:**
- Delete: see the full list in Step 1 below
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java`
- Modify: `gen-auth-starter/src/main/java/com/example/authsvc/api/advice/GlobalExceptionHandler.java`
- Modify: `gen-auth-starter/build.gradle` (remove mail/amqp/ses/rabbitmq-test dependencies)

**Interfaces:**
- Produces: the trimmed `SecurityConfig` (no `InternalHmacAuthFilter` dependency, no magic-link/super-admin/impersonation `permitAll()` entries) and `GlobalExceptionHandler` (no `MagicLinkInvalidException` handler) that Task 6's demo app boots against.

- [ ] **Step 1: Delete every out-of-scope source file**

```bash
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/controller/ImpersonationTokenController.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/controller/MagicLinkController.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/controller/ServiceTokenController.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/controller/SuperAdminController.java

git rm gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/ImpersonationTokenRequest.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/MagicLinkIssueRequest.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/dto/request/MagicLinkVerifyRequest.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/ImpersonationTokenResponse.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/MagicLinkIssueResponse.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/api/dto/response/MagicLinkVerifyResponse.java

git rm gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ImpersonationTokenServiceImpl.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/impl/MagicLinkServiceImpl.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/impl/ProviderBackedEmailService.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/impl/SmtpEmailService.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/impl/SuperAdminLoginServiceImpl.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/impl/SuperAdminMagicLinkServiceImpl.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/service/EmailService.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/service/MagicLinkService.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/service/ServiceTokenService.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/service/SuperAdminLoginService.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/application/service/SuperAdminMagicLinkService.java

git rm gen-auth-starter/src/main/java/com/example/authsvc/domain/model/AuthSession.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/domain/model/MagicLinkEntry.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/domain/port/MagicLinkStore.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/domain/port/LockoutStore.java

git rm gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisLockoutStore.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/cache/RedisMagicLinkStore.java

git rm gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/entity/PlatformSuperAdminEntity.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/persistence/repository/PlatformSuperAdminJpaRepository.java

git rm gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/filter/InternalHmacAuthFilter.java

git rm -r gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/email
git rm -r gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/messaging
git rm -r gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/seed

git rm gen-auth-starter/src/main/java/com/example/authsvc/config/properties/MagicLinkProperties.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/config/properties/MailProperties.java
git rm gen-auth-starter/src/main/java/com/example/authsvc/config/properties/SuperAdminProperties.java

git rm gen-auth-starter/src/main/java/com/example/authsvc/common/exception/MagicLinkInvalidException.java
```

- [ ] **Step 2: Find and delete any test files targeting the deleted classes**

The deleted classes above may have their own dedicated test files. Search for them explicitly rather than guessing filenames:

Run: `grep -rlE "MagicLink|SuperAdmin|ImpersonationToken|ServiceTokenService|InternalHmacAuthFilter|ProviderBackedEmailService|SmtpEmailService|PlatformSuperAdmin|RedisLockoutStore|RedisMagicLinkStore|domain\.model\.AuthSession|domain\.port\.(MagicLinkStore|LockoutStore)" gen-auth-starter/src/test/java`

For every file the search returns, read it to confirm it exclusively tests deleted functionality (not, e.g., a shared test utility that also happens to mention one of these names in a comment), then delete it with `git rm <path>`. If a returned file tests a MIX of kept and deleted functionality, don't delete it — instead just remove the parts referencing deleted classes, matching how Step 3 below trims `SecurityConfig`/`GlobalExceptionHandler` rather than deleting them outright.

- [ ] **Step 3: Trim SecurityConfig.java**

Replace the full file:

```java
// gen-auth-starter/src/main/java/com/example/authsvc/infrastructure/security/config/SecurityConfig.java — full file after this change
package com.example.authsvc.infrastructure.security.config;

import com.example.authsvc.config.properties.CorsProperties;
import com.example.authsvc.infrastructure.security.handler.JwtAccessDeniedHandler;
import com.example.authsvc.infrastructure.security.handler.JwtAuthEntryPoint;
import com.example.authsvc.infrastructure.security.filter.InternalTokenAuthFilter;
import com.example.authsvc.infrastructure.security.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter  jwtFilter;
    private final InternalTokenAuthFilter  internalTokenAuthFilter;
    private final JwtAuthEntryPoint        authEntryPoint;
    private final JwtAccessDeniedHandler   accessDeniedHandler;
    private final CorsProperties           corsProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        SecurityFilterChain chain = http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── Truly public auth endpoints — no JWT needed ──────────────
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/register"
                        ).permitAll()
                        // ── Internal service-to-service endpoints — secret-header protected ──
                        .requestMatchers("/internal/**").permitAll()
                        // ── JWKS, actuator, docs — always public ─────────────────────
                        .requestMatchers(
                                "/.well-known/jwks.json",
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/v1/docs", "/v1/docs/**",
                                "/swagger-ui/**",
                                "/v1/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**"
                        ).permitAll()
                        // ── Protected auth endpoints — JWT required ──────────────────
                        .requestMatchers(HttpMethod.GET,  "/api/v1/auth/session").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // internalTokenAuthFilter guards /internal/** with shared-secret header
                // Runs before JWT so internal callers don't need user tokens
                .addFilterBefore(internalTokenAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter,               UsernamePasswordAuthenticationFilter.class)
                .build();
        log.info("security.initialized stateless=true cors_origins={}", corsProperties.getAllowedOrigins());
        return chain;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        log.debug("cors.configured origins={} methods={}", corsProperties.getAllowedOrigins(), corsProperties.getAllowedMethods());
        return source;
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(jwtFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<InternalTokenAuthFilter> internalTokenFilterRegistration() {
        FilterRegistrationBean<InternalTokenAuthFilter> reg = new FilterRegistrationBean<>(internalTokenAuthFilter);
        reg.setEnabled(false);
        return reg;
    }
}
```

- [ ] **Step 4: Trim GlobalExceptionHandler.java**

Remove the `MagicLinkInvalidException` import and its handler method:

```java
// Remove this import:
import com.example.authsvc.common.exception.MagicLinkInvalidException;

// Remove this method:
    @ExceptionHandler(MagicLinkInvalidException.class)
    public ResponseEntity<ErrorResponse> handleMagicLinkInvalid(MagicLinkInvalidException ex,
                                                                  HttpServletRequest request) {
        log.info("magic_link.invalid path={}", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse(ex.getMessage()));
    }
```

Every other handler method stays unchanged.

- [ ] **Step 5: Trim gen-auth-starter/build.gradle's dependencies**

Remove these lines (their corresponding source is now deleted):

```groovy
    // Remove:
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
    implementation 'software.amazon.awssdk:ses'
    testImplementation 'org.testcontainers:rabbitmq'
    testImplementation 'org.springframework.amqp:spring-rabbit-test'
```

(`resilience4j`, `oauth2-client`/`saml2`, and `webflux` are left in place for this pass — none of them map cleanly to a single deleted feature the way mail/amqp/ses do, so trimming them isn't part of this task's scope. Revisit in a later cleanup if a real client integration flags the extra weight.)

- [ ] **Step 6: Run the full suite, fix any remaining compile errors**

Run: `./gradlew.bat :gen-auth-starter:compileJava :gen-auth-starter:compileTestJava`
Expected: if this fails, the error will name a missing class — trace it back to one of the deleted files and remove the dangling reference the same way Steps 3-4 did. Do not re-create any deleted class to satisfy a stray reference; find and remove the reference instead.

Run: `./gradlew.bat :gen-auth-starter:test`
Expected: `BUILD SUCCESSFUL`, fewer than 38 tests now (some removed in Step 2), 0 failures

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "trim gen-auth-starter to core-auth-only v1 scope"
```

---

### Task 4: Starter's own independent Flyway instance

**Files:**
- Move: `gen-auth-starter/src/main/resources/db/migration/*.sql` → `gen-auth-starter/src/main/resources/db/migration/genauth/*.sql`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/GenAuthFlywayInitializer.java`
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/GenAuthJpaDependsOnFlywayConfig.java`

**Interfaces:**
- Consumes: `javax.sql.DataSource` (whatever bean the host app's own `spring-boot-starter-data-jpa` autoconfiguration produces — the starter never configures its own DataSource, it uses the host's).
- Produces: migrations applied to the host app's database, in its own `flyway_schema_history_auth` table, guaranteed to complete before JPA's `EntityManagerFactory` bean validates the schema — consumed implicitly by every entity/repository in the starter (they need the tables to already exist).

**Why this needs `EntityManagerFactoryDependsOnPostProcessor`, not `ApplicationRunner`:** `ApplicationRunner`/`CommandLineRunner` beans execute *after* the Spring context has fully refreshed — but Hibernate's `ddl-auto: validate` check runs *during* context refresh, when the `EntityManagerFactory` bean is created. If migrations ran via an `ApplicationRunner`, JPA would validate the schema before Flyway ever touched the database, failing immediately on a fresh install. `EntityManagerFactoryDependsOnPostProcessor` is the same public Spring Boot API class Spring Boot's own Flyway/Liquibase autoconfiguration uses internally to solve this exact ordering problem — it adds a `depends-on` relationship from the JPA `EntityManagerFactory` bean definition to a named bean, guaranteeing that bean's `InitializingBean.afterPropertiesSet()` (or constructor, if it does the work eagerly) runs first.

- [ ] **Step 1: Move migration files under a non-default classpath location**

```bash
mkdir -p gen-auth-starter/src/main/resources/db/migration/genauth
git mv gen-auth-starter/src/main/resources/db/migration/V1__init.sql gen-auth-starter/src/main/resources/db/migration/genauth/V1__init.sql
git mv gen-auth-starter/src/main/resources/db/migration/V2__auth_user_roles.sql gen-auth-starter/src/main/resources/db/migration/genauth/V2__auth_user_roles.sql
git mv gen-auth-starter/src/main/resources/db/migration/V3__jwt_signing_keys.sql gen-auth-starter/src/main/resources/db/migration/genauth/V3__jwt_signing_keys.sql
```

Reason this matters: a real host app might separately depend on `spring-boot-starter-flyway` for its *own* schema, with its own Flyway autoconfiguration defaulting to scanning `classpath:db/migration`. If our migrations sat at that same default location, the host's own Flyway bean could discover and attempt to apply them too — a real conflict this library must defend against, since we don't control what else is on a consumer's classpath.

- [ ] **Step 2: Write the Flyway initializer**

```java
// gen-auth-starter/src/main/java/com/example/authsvc/config/GenAuthFlywayInitializer.java
package com.example.authsvc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs Gen_AUTH's own Flyway migrations against the host application's DataSource,
 * completely independent of however the host manages its own schema (Flyway,
 * Liquibase, or nothing at all). Uses a dedicated history table
 * ({@code flyway_schema_history_auth}) and a non-default classpath location
 * ({@code classpath:db/migration/genauth}) so it never collides with the host's
 * own migration tooling.
 *
 * <p>Must run before JPA's {@code EntityManagerFactory} bean validates the schema —
 * see {@link GenAuthJpaDependsOnFlywayConfig}, which enforces that ordering.
 */
@Slf4j
@Component("genAuthFlywayInitializer")
@RequiredArgsConstructor
public class GenAuthFlywayInitializer implements InitializingBean {

    private final DataSource dataSource;

    @Override
    public void afterPropertiesSet() {
        log.info("genauth.flyway.migrating");
        Flyway.configure()
                .dataSource(dataSource)
                .table("flyway_schema_history_auth")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(true)
                .locations("classpath:db/migration/genauth")
                .load()
                .migrate();
        log.info("genauth.flyway.migrated");
    }
}
```

- [ ] **Step 3: Write the ordering config**

```java
// gen-auth-starter/src/main/java/com/example/authsvc/config/GenAuthJpaDependsOnFlywayConfig.java
package com.example.authsvc.config;

import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.context.annotation.Configuration;

/**
 * Forces the host application's JPA {@code EntityManagerFactory} bean to depend on
 * {@link GenAuthFlywayInitializer}, so Gen_AUTH's migrations are guaranteed to have
 * already run by the time Hibernate's {@code ddl-auto: validate} check executes.
 * This is the same mechanism (a public Spring Boot API) Spring Boot's own built-in
 * Flyway/Liquibase autoconfiguration uses internally for the identical problem.
 */
@Configuration
public class GenAuthJpaDependsOnFlywayConfig extends EntityManagerFactoryDependsOnPostProcessor {

    public GenAuthJpaDependsOnFlywayConfig() {
        super("genAuthFlywayInitializer");
    }
}
```

- [ ] **Step 4: Compile check**

Run: `./gradlew.bat :gen-auth-starter:compileJava`
Expected: `BUILD SUCCESSFUL`

Full end-to-end proof (a real database actually migrating and JPA validating against it) happens in Task 6's demo boot and Task 8's manual verification — there's no meaningful unit test for "does Flyway run before JPA validates" short of a real Spring context, which the existing `RsaKeyConfigDbKeyIntegrationTest` pattern already covers for a narrower case and which is gated behind a Docker-availability check for the same sandbox reason noted in Task 2.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "add starter's own independent Flyway instance, ordered before JPA validation"
```

---

### Task 5: Auto-configuration entry point

**Files:**
- Create: `gen-auth-starter/src/main/java/com/example/authsvc/config/GenAuthAutoConfiguration.java`
- Create: `gen-auth-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

**Interfaces:**
- Produces: the mechanism by which a host app (in any package) picks up every `@Service`/`@RestController`/`@Repository`/`@Entity` in the starter, plus `GenAuthFlywayInitializer`/`GenAuthJpaDependsOnFlywayConfig` from Task 4 — consumed by Task 6's demo module (the first real proof this works outside the starter's own package).

- [ ] **Step 1: Write the auto-configuration class**

```java
// gen-auth-starter/src/main/java/com/example/authsvc/config/GenAuthAutoConfiguration.java
package com.example.authsvc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Makes every {@code @Service}/{@code @RestController}/{@code @Repository}/
 * {@code @ConfigurationProperties} class in the {@code com.example.authsvc}
 * package tree visible to a host application, regardless of that application's
 * own base package.
 *
 * <p>A host app's default component scan (rooted at its own
 * {@code @SpringBootApplication} class's package) would never reach classes
 * living in a dependency jar under a different package — this explicit
 * {@code @ComponentScan}/{@code @EntityScan}/{@code @EnableJpaRepositories}/
 * {@code @ConfigurationPropertiesScan} is what makes this a real,
 * host-package-independent Spring Boot starter rather than something that only
 * happens to work when co-located in the same package. The properties classes
 * (e.g. {@code JwtProperties}, {@code CookieProperties}) carry no
 * {@code @Component} annotation of their own — the original {@code AuthSvcApplication}
 * relied on its own {@code @ConfigurationPropertiesScan} for these; now that this
 * starter has no application entry point of its own, this class must carry that
 * scan directive instead, or those beans are never registered in any host app.
 *
 * <p>Discovered automatically via {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ComponentScan("com.example.authsvc")
@EntityScan("com.example.authsvc.infrastructure.persistence.entity")
@EnableJpaRepositories("com.example.authsvc.infrastructure.persistence.repository")
@ConfigurationPropertiesScan("com.example.authsvc")
public class GenAuthAutoConfiguration {
}
```

No explicit `@Import` for `GenAuthFlywayInitializer`/`GenAuthJpaDependsOnFlywayConfig` — both classes live under `com.example.authsvc.config`, already inside the `@ComponentScan("com.example.authsvc")` root above, the same way every other config class in that package (`JwtSignerConfig`, `RsaKeyConfig`, `SecurityConfig`) is already discovered by scan rather than explicit import. Adding both would risk a duplicate bean-definition conflict for no benefit — one discovery mechanism per package is enough.

- [ ] **Step 2: Register it**

```
# gen-auth-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
com.example.authsvc.config.GenAuthAutoConfiguration
```

- [ ] **Step 3: Compile check**

Run: `./gradlew.bat :gen-auth-starter:compileJava`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "add GenAuthAutoConfiguration: makes the starter discoverable from any host package"
```

---

### Task 6: gen-auth-demo module

**Files:**
- Create: `gen-auth-demo/src/main/java/com/example/gendemo/GenAuthDemoApplication.java`
- Move: `gen-auth-starter/src/main/java/com/example/authsvc/AuthSvcApplication.java` — delete it (superseded by the file above; the starter has no application entry point of its own)
- Create: `gen-auth-demo/src/main/resources/application.yaml`

**Interfaces:**
- Consumes: `GenAuthAutoConfiguration` (Task 5) via the standard Spring Boot auto-configuration import mechanism — no explicit `@Import`/`@ComponentScan` needed in the demo app itself, proving the starter is genuinely self-contained.
- Produces: a runnable application at `com.example.gendemo.GenAuthDemoApplication`, deliberately in a different package than the starter's classes (see Global Constraints) — this is the thing Task 8 boots for manual verification.

- [ ] **Step 1: Remove the starter's old application class**

```bash
git rm gen-auth-starter/src/main/java/com/example/authsvc/AuthSvcApplication.java
```

- [ ] **Step 2: Write the demo application class**

```java
// gen-auth-demo/src/main/java/com/example/gendemo/GenAuthDemoApplication.java
package com.example.gendemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference application proving gen-auth-starter auto-configures correctly from
 * outside its own package — this class lives in {@code com.example.gendemo},
 * not {@code com.example.authsvc}, specifically so the starter's beans can only
 * be found via {@link com.example.authsvc.config.GenAuthAutoConfiguration}, not
 * by accidental component-scan overlap.
 */
@SpringBootApplication
public class GenAuthDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenAuthDemoApplication.class, args);
    }
}
```

- [ ] **Step 3: Write the demo's application.yaml**

Trimmed from the original: drops `spring.mail`, `spring.rabbitmq`, `mail.*`, `magic-link.*`, `impersonation.*`, `super-admin.*` (all deferred-feature config); adds `app.cookie.*`/`app.cors.*` (the properties the codebase reference audit found were previously orphaned — `CookieProperties`/`CorsProperties` bind to `app.cookie.*`/`app.cors.*`, but no prior `application.yaml` in this codebase ever set those keys, so both classes silently ran on their in-code defaults; this is the first config file to actually exercise them).

```yaml
# gen-auth-demo/src/main/resources/application.yaml
server:
  port: 8101
  shutdown: graceful
  forward-headers-strategy: framework
  tomcat:
    threads:
      max: 200
      min-spare: 20
    accept-count: 100
    max-connections: 1000

spring:
  main:
    allow-bean-definition-overriding: true

  application:
    name: gen-auth-demo

  threads:
    virtual:
      enabled: true

  datasource:
    url: ${AUTH_DB_URL}
    username: ${AUTH_DB_USERNAME}
    password: ${AUTH_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

    hikari:
      pool-name: auth-hikari-pool
      maximum-pool-size: 200
      minimum-idle: 20
      idle-timeout: 30000
      max-lifetime: 1800000
      connection-timeout: 30000
      leak-detection-threshold: 60000

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    properties:
      hibernate:
        format_sql: true
        jdbc:
          time_zone: UTC
    show-sql: false

  data:
    redis:
      host: localhost
      port: 6379
      ssl:
        enabled: false
      timeout: 5s
      lettuce:
        pool:
          enabled: true
          max-active: 20
          max-idle: 10
          min-idle: 5

  session:
    store-type: none

management:
  endpoints:
    web:
      exposure:
        include:
          - health
          - info
          - metrics
          - prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    root: INFO
    org.springframework.security: INFO
    org.hibernate.SQL: WARN
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# ===================================================================
# CORS CONFIG — app.cors.* (previously orphaned; CorsProperties binds here)
# ===================================================================

app:
  cors:
    allowed-origins:
      - http://localhost:3000
      - http://localhost:4200
    allowed-methods:
      - GET
      - POST
      - PUT
      - PATCH
      - DELETE
      - OPTIONS
    allowed-headers:
      - Authorization
      - Content-Type
      - Accept
      - X-Requested-With
    allow-credentials: true
    max-age: 3600

  # ===================================================================
  # COOKIE CONFIG — app.cookie.* (previously orphaned; CookieProperties binds here)
  # ===================================================================
  cookie:
    domain: ${COOKIE_DOMAIN:}
    secure: ${COOKIE_SECURE:false}
    http-only: true
    same-site: Strict
    path: /

# ===================================================================
# JWT CONFIG
# ===================================================================

jwt:
  issuer: ${JWT_ISSUER:https://auth.example.com}
  audience: ${JWT_AUDIENCE:example-api}
  key-id: auth-key-v1
  signing-mode: local
  active-kid: auth-key-v1

  access-token:
    expiration-minutes: 15

  refresh-token:
    expiration-days: 7

  keys:
    - kid: auth-key-v1
      private-key-path: classpath:keys/private.pem
      public-key-path: classpath:keys/public.pem

  key-encryption-secret: ${JWT_KEY_ENCRYPTION_SECRET}

# ===================================================================
# AWS CONFIG (only used when jwt.signing-mode=kms)
# ===================================================================

aws:
  region: ${AWS_REGION:ap-south-1}
  access-key-id: ${AWS_ACCESS_KEY_ID:}
  secret-access-key: ${AWS_SECRET_ACCESS_KEY:}
  kms:
    key-id: ${AWS_KMS_KEY_ID:}

# ===================================================================
# LOCKOUT / PERMISSION CACHE
# ===================================================================

lockout:
  max-attempts: 5
  window-minutes: 15
  lock-duration-minutes: 15

permission-cache:
  ttl-minutes: 5

# ===================================================================
# INTERNAL SERVICE AUTH
# ===================================================================

internal-service-secret: ${INTERNAL_SERVICE_SECRET}

# ===================================================================
# SWAGGER
# ===================================================================

springdoc:
  api-docs:
    enabled: true
    path: /v1/docs/openapi.json
  swagger-ui:
    enabled: true
    path: /v1/docs
    operationsSorter: method
    tagsSorter: alpha
    disable-swagger-default-url: true
    document-title: Gen_AUTH Demo
  show-actuator: false

# ===================================================================
# CUSTOM AUTH CONFIG
# ===================================================================

auth:
  registration-mode: ${REGISTRATION_MODE:open}
  token-delivery-mode: ${TOKEN_DELIVERY_MODE:cookie}

  session:
    concurrent-session-limit: 5

  refresh-token:
    rotate-on-refresh: true
    one-time-use: true

  permission-cache:
    enabled: true

  audit:
    enabled: true
```

- [ ] **Step 4: Generate a JWT keypair for the demo module (gitignored, same as the starter's own dev setup)**

```bash
mkdir -p gen-auth-demo/src/main/resources/keys
openssl genrsa -out gen-auth-demo/src/main/resources/keys/private.pem 2048
openssl rsa -in gen-auth-demo/src/main/resources/keys/private.pem -pubout -out gen-auth-demo/src/main/resources/keys/public.pem
```

- [ ] **Step 5: Boot the demo app against real Postgres + Redis**

```bash
docker compose up -d   # reuses the existing docker-compose.yml at repo root: Postgres 5433, Redis 6380
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC \
AUTH_DB_URL=jdbc:postgresql://localhost:5433/genauth \
AUTH_DB_USERNAME=postgres AUTH_DB_PASSWORD=postgres \
SPRING_DATA_REDIS_PORT=6380 \
INTERNAL_SERVICE_SECRET=dev-secret \
JWT_KEY_ENCRYPTION_SECRET=$(openssl rand -base64 32) \
JWT_ISSUER=genauth-demo JWT_AUDIENCE=genauth-demo \
./gradlew.bat :gen-auth-demo:bootRun
```

Expected: boots clean, logs show `genauth.flyway.migrating` / `genauth.flyway.migrated` (from Task 4) before Hibernate's schema validation, then `Started GenAuthDemoApplication`. If Hibernate reports a "missing table" error, the `EntityManagerFactoryDependsOnPostProcessor` wiring from Task 4 isn't taking effect — stop and investigate before continuing, don't paper over it with `ddl-auto: update`.

Full endpoint-level verification (register/login/refresh/rotate) happens in Task 8 — this step only proves the app boots.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "add gen-auth-demo module: reference app proving the starter auto-configures"
```

---

### Task 7: GitHub Packages publishing

**Files:**
- Modify: `gen-auth-starter/build.gradle` (add `maven-publish` plugin + publication + repository blocks)

**Interfaces:**
- Produces: a publishable Maven artifact `com.example:gen-auth-starter:0.0.1-SNAPSHOT` — consumed by any future client project's `build.gradle` via `implementation 'com.example:gen-auth-starter:<version>'` once actually pushed to the registry (a separate, later step needing live org credentials — not part of this task's verification).

- [ ] **Step 1: Add the maven-publish plugin and publication config**

```groovy
// gen-auth-starter/build.gradle — add near the top, after the existing plugins block
plugins {
    id 'java-library'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'maven-publish'
}
```

```groovy
// gen-auth-starter/build.gradle — append at the end of the file
publishing {
    publications {
        maven(MavenPublication) {
            groupId = 'com.example'
            artifactId = 'gen-auth-starter'
            version = project.version
            from components.java
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YOUR_GITHUB_ORG/Gen_Auth")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

Replace `YOUR_GITHUB_ORG` with the actual GitHub org/user this repo lives under before the first real remote publish — the exact value depends on where this repo is hosted, which isn't something this plan can know in advance.

- [ ] **Step 2: Verify the publication is well-formed, without needing live credentials**

Run: `./gradlew.bat :gen-auth-starter:publishToMavenLocal`
Expected: `BUILD SUCCESSFUL`, artifact appears under `~/.m2/repository/com/example/gen-auth-starter/0.0.1-SNAPSHOT/`

This is the correct stopping point for this task — a real push to GitHub Packages needs live `GITHUB_ACTOR`/`GITHUB_TOKEN` credentials for the actual org, which this environment doesn't have and this plan can't fabricate. Publishing for real is a manual or CI step, done once the org/repo placeholder above is filled in.

- [ ] **Step 3: Commit**

```bash
git add gen-auth-starter/build.gradle
git commit -m "add GitHub Packages publish config for gen-auth-starter"
```

---

### Task 8: Manual verification + README

**Files:**
- Modify: `README.md`

**Interfaces:**
- Consumes: everything from Tasks 1-7 — this is the final proof pass, same convention as the manual-verification tasks in the Flyway-fix and JWKS-rotation slices.

- [ ] **Step 1: Update README's module-layout section**

Add near the top of `README.md`, replacing the single-module framing:

```markdown
## Module layout

- `gen-auth-starter/` — the reusable auth library. A Spring Boot starter: add it as a dependency, get a working core-auth module (register/login/refresh/logout/session/change-password + JWKS with runtime key rotation) auto-configured into your own app, no shared code between deployments, no dependency back on this repo after you've pulled the artifact. Magic-link reset, super-admin/impersonation, RabbitMQ events, and email sending are not included in this v1 scope (see `docs/superpowers/specs/2026-07-16-starter-library-design.md`).
- `gen-auth-demo/` — a thin reference app (package `com.example.gendemo`, deliberately not `com.example.authsvc`) that depends on the starter. Run this the same way the old standalone service ran; it exists to prove the starter's auto-configuration genuinely works from outside its own package, and to double as a working example of what a consuming project's `build.gradle`/`application.yaml` need to look like.
```

- [ ] **Step 2: Run the manual verification pass against gen-auth-demo**

With the demo app running from Task 6's Step 5, exercise the full core-auth lifecycle:

```bash
# 1. Register
curl -s -X POST localhost:8101/api/v1/auth/register -H 'content-type: application/json' \
  -d '{"email":"demo@example.com","password":"DemoPass123"}'
# Expected: 201, {"userId":"<uuid>"}

# 2. Login
curl -s -X POST localhost:8101/api/v1/auth/login -H 'content-type: application/json' \
  -d '{"email":"demo@example.com","password":"DemoPass123"}'
# Expected: 200, Set-Cookie headers present (cookie mode is the default)

# 3. JWKS
curl -s localhost:8101/.well-known/jwks.json
# Expected: 200, {"keys":[{"kid":"auth-key-v1",...}]}

# 4. Rotate the signing key
curl -s -i -X POST localhost:8101/internal/auth/keys/rotate -H 'X-Internal-Secret: dev-secret'
# Expected: 201, {"kid":"auth-key-<uuid>","publicKeyPem":"..."}

# 5. JWKS now shows 2 keys
curl -s localhost:8101/.well-known/jwks.json
# Expected: "keys" array has 2 entries

# 6. Session (using the cookie from step 2 — pass -b/--cookie-jar as needed for your curl setup)
curl -s localhost:8101/api/v1/auth/session -b cookies.txt
# Expected: 200, session details for demo@example.com
```

Expected: every step matches its inline "Expected" note. If any step fails, determine whether it's a genuine bug introduced by this restructuring (most likely: a missed `@ComponentScan` path, a dangling reference to a deleted class, or the Flyway-before-JPA ordering from Task 4) versus a pre-existing behavior — fix restructuring bugs before considering this task done; do not silently work around them.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "document module layout, run manual verification against gen-auth-demo"
```

## Self-Review Notes

- **Spec coverage:** multi-module split ✅ (Task 1), pure mechanical relocation ✅ (Task 2), core-auth-only scope trim ✅ (Task 3), independent Flyway instance with correct JPA ordering ✅ (Task 4), package-independent auto-configuration ✅ (Task 5), demo module in a genuinely different package ✅ (Task 6), GitHub Packages publish config ✅ (Task 7), manual verification + README ✅ (Task 8), orphaned `app.cookie.*`/`app.cors.*` fix ✅ (Task 6's demo `application.yaml`).
- **Placeholder scan:** none — every step has runnable, complete code. The one intentional placeholder (`YOUR_GITHUB_ORG` in Task 7) is explicitly called out as something to fill in with real info this plan cannot know, not a forgotten TODO.
- **Type consistency:** `GenAuthFlywayInitializer`'s bean name (`"genAuthFlywayInitializer"`, Task 4 Step 2) matches exactly what `GenAuthJpaDependsOnFlywayConfig`'s constructor references (Task 4 Step 3); both classes are picked up by `GenAuthAutoConfiguration`'s `@ComponentScan("com.example.authsvc")` (Task 5 Step 1) with no separate registration needed.
