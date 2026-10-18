# Gen_TNT Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Gen_TNT's core (Tenant CRUD + the webhook-driven, configurable provisioning saga) as a two-module Gradle project — `gen-tnt-starter` (embeddable library) + `gen-tnt-demo` (thin deployable reference app) — genericized from CPMS-Platform's `tnt-svc`, mirroring Gen_Auth's own starter/demo structure exactly.

**Architecture:** All real logic (domain, persistence, saga orchestrator, schedulers, web layer) lives in `gen-tnt-starter`, auto-configured onto any host app via `GenTntAutoConfiguration` (component-scan + entity-scan + JPA-repo-scan + config-properties-scan, same idiom as `GenAuthAutoConfiguration`). `gen-tnt-demo` is just a `@SpringBootApplication` entrypoint + `application.yaml` + docker-compose for local dev — it deploys the starter standalone so non-Java consumers (PMP) can call it over HTTP.

**Tech Stack:** Java 21, Spring Boot 4.0.6, Postgres 15, Redis, Flyway, Spring Data JPA, Lombok, JUnit 5 + Testcontainers (real Postgres/Redis in tests, no mocked DB).

## Global Constraints

- No RabbitMQ, no gRPC, no `io.cpms.common.security` — all dropped per the design spec (`docs/superpowers/specs/2026-07-20-gen-tnt-provisioning-design.md`).
- No security-starter/OAuth2/JWT — auth is a single `X-Internal-Secret` header check, nothing more.
- No swagger/OpenAPI UI, no resilience4j, no observability/tracing beyond default logging, no MapStruct — none of these were asked for; add only if a real need shows up later.
- Package base for `gen-tnt-starter`: `com.example.tnt_svc` (keeps the original CPMS package name, same choice Gen_Auth made keeping `com.example.authsvc`). Package base for `gen-tnt-demo`: `com.example.tntdemo`.
- Step pipeline is config-driven (`gentnt.provisioning.steps` in `application.yaml`), strictly sequential — no parallel-fan-out group (deferred per spec).
- Every entity ID is `UUID` via `@GeneratedValue(strategy = GenerationType.UUID)`. Every timestamp is `Instant`.
- `ddl-auto: validate` — Flyway owns the schema, Hibernate never auto-generates DDL.
- Tests use real Postgres + Redis via Testcontainers, never mocks of the database — matching Gen_Auth's own testing philosophy.
- Lombok (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`) on entities to cut boilerplate — already an established dependency pattern in this org's Java services.

---

### Task 1: Gradle multi-module scaffold

**Files:**

- Create: `settings.gradle`
- Create: `gen-tnt-starter/build.gradle`
- Create: `gen-tnt-demo/build.gradle`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/config/GenTntAutoConfiguration.java`
- Create: `gen-tnt-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `gen-tnt-demo/src/main/java/com/example/tntdemo/GenTntDemoApplication.java`
- Create: `gen-tnt-demo/src/main/resources/application.yaml`
- Create: `docker-compose.yml`
- Create: `.gitignore`
- Copy: `gradle/`, `gradlew`, `gradlew.bat` from the sibling Gen_Auth repo (same Java 21 / Gradle version already proven to work — no need to bootstrap a wrapper from scratch)

**Interfaces:** none yet — this task only needs to produce a running, empty Spring Boot app.

- [ ] **Step 1: Copy the Gradle wrapper from Gen_Auth**

```bash
cd "C:/Users/naksh/Desktop/Metaupspace/Gen_MS/Gen_TNT"
cp -r ../Gen_AUTH/gradle .
cp ../Gen_AUTH/gradlew ../Gen_AUTH/gradlew.bat .
```

- [ ] **Step 2: Write settings.gradle**

```groovy
// settings.gradle
rootProject.name = 'gen-tnt'
include(':gen-tnt-starter', ':gen-tnt-demo')

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

- [ ] **Step 3: Write gen-tnt-starter/build.gradle**

```groovy
// gen-tnt-starter/build.gradle
plugins {
    id 'java-library'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'maven-publish'
}

description = 'gen-tnt-starter'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

configurations {
    compileOnly {
        extendsFrom annotationProcessor
    }
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.boot:spring-boot-dependencies:4.0.6"
        mavenBom "org.testcontainers:testcontainers-bom:1.21.4"
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    // Spring Boot 4 moved RestClient.Builder's autoconfiguration out of
    // spring-boot-starter-web into its own module (spring-boot-restclient) —
    // without this, any @Component constructor-injecting RestClient.Builder
    // (StepWebhookClient, Task 5) fails to find a bean for it.
    implementation 'org.springframework.boot:spring-boot-starter-restclient'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    runtimeOnly 'org.postgresql:postgresql'
    // Spring Boot 4 moved FlywayAutoConfiguration out of spring-boot-autoconfigure
    // into its own module — flyway-core alone no longer gets Flyway auto-run on
    // startup, this starter is what actually wires it in.
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.flywaydb:flyway-core'
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
    testImplementation 'org.testcontainers:testcontainers'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
    // JVM's default timezone can resolve to a legacy IANA alias (e.g.
    // Asia/Calcutta instead of Asia/Kolkata) that the Testcontainers Postgres
    // image's tzdata doesn't recognize, failing every DB connection with
    // 'invalid value for parameter "TimeZone"'. Forcing UTC here means every
    // contributor gets a working `./gradlew test` regardless of locale,
    // instead of needing to remember a JAVA_TOOL_OPTIONS env var.
    jvmArgs '-Duser.timezone=UTC'
}

publishing {
    publications {
        maven(MavenPublication) {
            groupId = 'com.example'
            artifactId = 'gen-tnt-starter'
            version = project.version
            from components.java
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/YOUR_GITHUB_ORG/Gen_TNT")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

- [ ] **Step 4: Write gen-tnt-demo/build.gradle**

```groovy
// gen-tnt-demo/build.gradle
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
}

description = 'gen-tnt-demo'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation project(':gen-tnt-starter')
    // gen-tnt-starter declares its Spring Boot deps as `implementation`, which
    // java-library does not expose transitively — this module needs
    // spring-boot-starter directly on its own compile classpath for
    // @SpringBootApplication (same reason gen-auth-demo needs it).
    implementation 'org.springframework.boot:spring-boot-starter'
    developmentOnly 'org.springframework.boot:spring-boot-devtools'
}
```

- [ ] **Step 5: Write the auto-configuration entrypoint**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/config/GenTntAutoConfiguration.java
package com.example.tnt_svc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Makes every {@code @Service}/{@code @RestController}/{@code @Repository}/
 * {@code @ConfigurationProperties} class in the {@code com.example.tnt_svc}
 * package tree visible to a host application, regardless of that
 * application's own base package — same idiom as Gen_Auth's
 * {@code GenAuthAutoConfiguration}. Discovered automatically via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@EnableScheduling
@ComponentScan("com.example.tnt_svc")
@EntityScan("com.example.tnt_svc.domain")
@EnableJpaRepositories("com.example.tnt_svc.persistence")
@ConfigurationPropertiesScan("com.example.tnt_svc")
public class GenTntAutoConfiguration {
}
```

- [ ] **Step 6: Register the auto-configuration**

```
com.example.tnt_svc.config.GenTntAutoConfiguration
```

(This is the entire content of `gen-tnt-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.)

- [ ] **Step 7: Write the demo app entrypoint**

```java
// gen-tnt-demo/src/main/java/com/example/tntdemo/GenTntDemoApplication.java
package com.example.tntdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference application proving gen-tnt-starter auto-configures correctly
 * from outside its own package — lives in {@code com.example.tntdemo}, not
 * {@code com.example.tnt_svc}, so the starter's beans can only be found via
 * {@link com.example.tnt_svc.config.GenTntAutoConfiguration}.
 */
@SpringBootApplication
public class GenTntDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenTntDemoApplication.class, args);
    }
}
```

- [ ] **Step 8: Write application.yaml**

```yaml
# gen-tnt-demo/src/main/resources/application.yaml
server:
  port: 8201

spring:
  application:
    name: gen-tnt-demo

  datasource:
    url: ${TNT_DB_URL}
    username: ${TNT_DB_USERNAME}
    password: ${TNT_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true

  data:
    redis:
      host: ${TNT_REDIS_HOST:localhost}
      port: ${TNT_REDIS_PORT:6381}

management:
  endpoints:
    web:
      exposure:
        include: health

gentnt:
  internal-secret: ${INTERNAL_SERVICE_SECRET}
  provisioning:
    max-retries: 3
    timeout-minutes: 10
    retry-scheduler-interval-ms: 120000
    timeout-scheduler-interval-ms: 60000
    steps: []
```

- [ ] **Step 9: Write docker-compose.yml for local dev**

```yaml
# docker-compose.yml
# Ports 5435/6381 — deliberately distinct from PMP's app postgres (5434, after
# its own native-Postgres collision fix), Gen_Auth's auth-postgres/auth-redis
# (5433/6380), and PMP's app redis (6379) — so all four repos' local dev
# stacks can run side by side on the same machine without a port fight.
services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
      POSTGRES_DB: gentnt
    ports:
      - "5435:5432"
    volumes:
      - gentnt_postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7
    ports:
      - "6381:6379"

volumes:
  gentnt_postgres_data:
```

- [ ] **Step 10: Write .gitignore**

```
# .gitignore
.gradle/
build/
.idea/
*.iml
.env
```

- [ ] **Step 11: Verify the build**

Run: `./gradlew.bat build -x test` (no tests exist yet)
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 12: Commit**

```bash
git add settings.gradle gen-tnt-starter gen-tnt-demo docker-compose.yml .gitignore gradle gradlew gradlew.bat
git commit -m "feat: scaffold gen-tnt-starter/gen-tnt-demo Gradle multi-module project"
```

---

### Task 2: Tenant status state machine + slug validator

**Files:**

- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/TenantStatus.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/InvalidTenantStateTransitionException.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/TenantStateMachine.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/domain/TenantStateMachineTest.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/validation/ValidSlug.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/validation/SlugValidator.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/validation/SlugValidatorTest.java`

**Interfaces:**

- Produces: `TenantStatus` enum (`PROVISIONING, ACTIVE, SUSPENDED, CANCELLED, PURGED`), `TenantStateMachine.validateTransition(TenantStatus from, TenantStatus to)` (throws `InvalidTenantStateTransitionException` on an illegal transition, does nothing on a legal one), `TenantStateMachine.isTransitionAllowed(TenantStatus from, TenantStatus to): boolean`. `@ValidSlug` annotation for Bean Validation on any `String` field. Consumed by Task 3 (`Tenant` entity) and Task 4 (`TenantService`, `CreateTenantRequest`).

- [ ] **Step 1: Write the failing tests**

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/domain/TenantStateMachineTest.java
package com.example.tnt_svc.domain;

import com.example.tnt_svc.domain.exception.InvalidTenantStateTransitionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantStateMachineTest {

    @Test
    void allowsProvisioningToActive() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.PROVISIONING, TenantStatus.ACTIVE)).isTrue();
    }

    @Test
    void allowsProvisioningToCancelled() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.PROVISIONING, TenantStatus.CANCELLED)).isTrue();
    }

    @Test
    void allowsActiveToSuspendedAndBack() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.ACTIVE, TenantStatus.SUSPENDED)).isTrue();
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.SUSPENDED, TenantStatus.ACTIVE)).isTrue();
    }

    @Test
    void allowsCancelledToPurged() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.CANCELLED, TenantStatus.PURGED)).isTrue();
    }

    @Test
    void rejectsAnyTransitionOutOfPurged() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.PURGED, TenantStatus.ACTIVE)).isFalse();
    }

    @Test
    void rejectsSkippingStraightToSuspendedFromProvisioning() {
        assertThat(TenantStateMachine.isTransitionAllowed(TenantStatus.PROVISIONING, TenantStatus.SUSPENDED)).isFalse();
    }

    @Test
    void validateTransitionThrowsOnIllegalTransition() {
        assertThatThrownBy(() -> TenantStateMachine.validateTransition(TenantStatus.PURGED, TenantStatus.ACTIVE))
            .isInstanceOf(InvalidTenantStateTransitionException.class)
            .hasMessageContaining("PURGED")
            .hasMessageContaining("ACTIVE");
    }

    @Test
    void validateTransitionDoesNotThrowOnLegalTransition() {
        TenantStateMachine.validateTransition(TenantStatus.ACTIVE, TenantStatus.CANCELLED);
    }
}
```

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/validation/SlugValidatorTest.java
package com.example.tnt_svc.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SlugValidatorTest {

    private final SlugValidator validator = new SlugValidator();

    @Test
    void acceptsLowercaseAlphanumericWithHyphens() {
        assertThat(validator.isValid("acme-corp", null)).isTrue();
        assertThat(validator.isValid("abc", null)).isTrue();
    }

    @Test
    void rejectsUppercase() {
        assertThat(validator.isValid("Acme", null)).isFalse();
    }

    @Test
    void rejectsLeadingDigit() {
        assertThat(validator.isValid("1acme", null)).isFalse();
    }

    @Test
    void rejectsTrailingHyphen() {
        assertThat(validator.isValid("acme-", null)).isFalse();
    }

    @Test
    void rejectsTooShort() {
        assertThat(validator.isValid("ab", null)).isFalse();
    }

    @Test
    void rejectsTooLong() {
        assertThat(validator.isValid("a".repeat(64), null)).isFalse();
    }

    @Test
    void acceptsMinAndMaxLength() {
        assertThat(validator.isValid("abc", null)).isTrue();
        assertThat(validator.isValid("a" + "b".repeat(61) + "c", null)).isTrue();
    }

    @Test
    void rejectsNull() {
        assertThat(validator.isValid(null, null)).isFalse();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.domain.TenantStateMachineTest" --tests "com.example.tnt_svc.validation.SlugValidatorTest"`
Expected: FAIL — compile errors, none of `TenantStatus`/`TenantStateMachine`/`InvalidTenantStateTransitionException`/`SlugValidator` exist yet.

- [ ] **Step 3: Write TenantStatus and the exception**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/TenantStatus.java
package com.example.tnt_svc.domain;

public enum TenantStatus {
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    CANCELLED,
    PURGED
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/InvalidTenantStateTransitionException.java
package com.example.tnt_svc.domain.exception;

import com.example.tnt_svc.domain.TenantStatus;

public class InvalidTenantStateTransitionException extends RuntimeException {

    public InvalidTenantStateTransitionException(TenantStatus from, TenantStatus to) {
        super("Cannot transition tenant from " + from + " to " + to);
    }
}
```

- [ ] **Step 4: Write TenantStateMachine**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/TenantStateMachine.java
package com.example.tnt_svc.domain;

import com.example.tnt_svc.domain.exception.InvalidTenantStateTransitionException;

import java.util.Map;
import java.util.Set;

/** Pure, stateless — no Spring, no persistence. Portable as-is from tnt-svc. */
public final class TenantStateMachine {

    private static final Map<TenantStatus, Set<TenantStatus>> ALLOWED_TRANSITIONS = Map.of(
        TenantStatus.PROVISIONING, Set.of(TenantStatus.ACTIVE, TenantStatus.CANCELLED),
        TenantStatus.ACTIVE, Set.of(TenantStatus.SUSPENDED, TenantStatus.CANCELLED),
        TenantStatus.SUSPENDED, Set.of(TenantStatus.ACTIVE, TenantStatus.CANCELLED),
        TenantStatus.CANCELLED, Set.of(TenantStatus.PURGED),
        TenantStatus.PURGED, Set.of()
    );

    private TenantStateMachine() {
    }

    public static boolean isTransitionAllowed(TenantStatus from, TenantStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(TenantStatus from, TenantStatus to) {
        if (!isTransitionAllowed(from, to)) {
            throw new InvalidTenantStateTransitionException(from, to);
        }
    }
}
```

- [ ] **Step 5: Write ValidSlug and SlugValidator**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/validation/ValidSlug.java
package com.example.tnt_svc.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SlugValidator.class)
public @interface ValidSlug {
    String message() default "slug must be lowercase alphanumeric with hyphens, 3-63 characters, not starting/ending with a hyphen";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/validation/SlugValidator.java
package com.example.tnt_svc.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/** Ported as-is from tnt-svc — confirmed zero CPMS coupling in the genericization audit. */
public class SlugValidator implements ConstraintValidator<ValidSlug, String> {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z][a-z0-9-]*[a-z0-9]$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        if (value.length() < 3 || value.length() > 63) {
            return false;
        }
        return SLUG_PATTERN.matcher(value).matches();
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.domain.TenantStateMachineTest" --tests "com.example.tnt_svc.validation.SlugValidatorTest"`
Expected: PASS (16 tests)

- [ ] **Step 7: Commit**

```bash
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/domain gen-tnt-starter/src/main/java/com/example/tnt_svc/validation gen-tnt-starter/src/test
git commit -m "feat: add tenant state machine and slug validator"
```

---

### Task 3: Tenant, ProvisioningJob, ProvisioningStep entities + repositories + migration

**Files:**

- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/DuplicateSlugException.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/TenantNotFoundException.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/ProvisioningJobNotFoundException.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/Tenant.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/ProvisioningJobStatus.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/ProvisioningStepStatus.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/ProvisioningJob.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/ProvisioningStep.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence/JsonMapConverter.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence/TenantRepository.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence/ProvisioningJobRepository.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence/ProvisioningStepRepository.java`
- Create: `gen-tnt-starter/src/main/resources/db/migration/gentnt/V1__tenant_and_provisioning.sql`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/AbstractIntegrationTest.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/domain/TenantTest.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/domain/ProvisioningJobTest.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/persistence/TenantRepositoryTest.java`

**Interfaces:**

- Consumes: `TenantStatus`, `TenantStateMachine` (Task 2).
- Produces: `Tenant` entity (`id, slug, name, status, region, primaryOwnerUserId, provisioningJobId, idempotencyKey, createdAt, updatedAt` + `transitionTo(TenantStatus)`, `suspend()`, `reactivate()`, `cancel()`, `activateAfterProvisioning()`, `purge()`), `ProvisioningJob` entity (`id, tenantId, status, retryCount, maxRetries, callbackToken, lastError, context (Map<String,Object>), startedAt, completedAt, expiresAt, createdAt` + `markInProgress()`, `markCompleted()`, `markFailed(String)`, `markDead()`, `canRetry()`, `incrementRetry()`, `mergeContext(Map<String,Object>)`), `ProvisioningStep` entity (`id, jobId, stepName, stepOrder, status, retryCount, errorMessage, startedAt, completedAt` + `markInProgress()`, `markCompleted()`, `markFailed(String)`, `resetToPending()`), `TenantRepository`, `ProvisioningJobRepository`, `ProvisioningStepRepository`. All consumed by Task 4 (`TenantService`) and Task 8 (`ProvisioningSagaOrchestrator`).

- [ ] **Step 1: Write the failing tests**

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/AbstractIntegrationTest.java
package com.example.tnt_svc;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared real-Postgres base for every integration test in this module — no mocked DB.
 *
 * <p>{@code @EnableAutoConfiguration} is required alongside {@code @SpringBootTest(classes=...)}:
 * passing an explicit {@code classes} array does not by itself trigger Spring Boot's
 * autoconfiguration import chain (DataSource/JPA/Flyway all live behind it) the way a real
 * {@code @SpringBootApplication} entrypoint would — {@code gen-tnt-starter} has no such
 * entrypoint of its own (that only exists in {@code gen-tnt-demo}), so tests here have to
 * ask for it explicitly.
 */
@Testcontainers
@EnableAutoConfiguration
@SpringBootTest(classes = com.example.tnt_svc.config.GenTntAutoConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("gentnt_test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration/gentnt");
        registry.add("gentnt.internal-secret", () -> "test-secret");
    }
}
```

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/domain/TenantTest.java
package com.example.tnt_svc.domain;

import com.example.tnt_svc.domain.exception.InvalidTenantStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantTest {

    private Tenant newTenant() {
        return Tenant.builder()
            .slug("acme")
            .name("Acme Corp")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build();
    }

    @Test
    void activateAfterProvisioningMovesFromProvisioningToActive() {
        Tenant tenant = newTenant();
        tenant.activateAfterProvisioning();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void suspendRequiresActiveStatus() {
        Tenant tenant = newTenant();
        assertThatThrownBy(tenant::suspend).isInstanceOf(InvalidTenantStateTransitionException.class);
    }

    @Test
    void suspendThenReactivateRoundTrips() {
        Tenant tenant = newTenant();
        tenant.activateAfterProvisioning();
        tenant.suspend();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
        tenant.reactivate();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void purgeRequiresCancelledStatus() {
        Tenant tenant = newTenant();
        assertThatThrownBy(tenant::purge).isInstanceOf(InvalidTenantStateTransitionException.class);
    }

    @Test
    void cancelThenPurgeRoundTrips() {
        Tenant tenant = newTenant();
        tenant.cancel();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.CANCELLED);
        tenant.purge();
        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PURGED);
    }
}
```

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/domain/ProvisioningJobTest.java
package com.example.tnt_svc.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningJobTest {

    private ProvisioningJob newJob() {
        return ProvisioningJob.builder()
            .tenantId(UUID.randomUUID())
            .status(ProvisioningJobStatus.PENDING)
            .retryCount(0)
            .maxRetries(3)
            .callbackToken(UUID.randomUUID().toString())
            .context(Map.of())
            .build();
    }

    @Test
    void canRetryIsTrueWhenFailedAndUnderMax() {
        ProvisioningJob job = newJob();
        job.markInProgress();
        job.markFailed("boom");
        assertThat(job.canRetry()).isTrue();
    }

    @Test
    void canRetryIsFalseWhenRetryCountReachesMax() {
        ProvisioningJob job = newJob();
        job.markInProgress();
        job.markFailed("boom");
        job.incrementRetry();
        job.incrementRetry();
        job.incrementRetry();
        assertThat(job.canRetry()).isFalse();
    }

    @Test
    void canRetryIsFalseWhenNotFailed() {
        ProvisioningJob job = newJob();
        assertThat(job.canRetry()).isFalse();
    }

    @Test
    void mergeContextAddsKeysWithoutDroppingExisting() {
        ProvisioningJob job = newJob();
        job.mergeContext(Map.of("a", "1"));
        job.mergeContext(Map.of("b", "2"));
        assertThat(job.getContext()).containsEntry("a", "1").containsEntry("b", "2");
    }

    @Test
    void markCompletedSetsCompletedAt() {
        ProvisioningJob job = newJob();
        job.markInProgress();
        job.markCompleted();
        assertThat(job.getStatus()).isEqualTo(ProvisioningJobStatus.COMPLETED);
        assertThat(job.getCompletedAt()).isNotNull();
    }
}
```

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/persistence/TenantRepositoryTest.java
package com.example.tnt_svc.persistence;

import com.example.tnt_svc.AbstractIntegrationTest;
import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TenantRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void savesAndFindsBySlug() {
        Tenant tenant = Tenant.builder()
            .slug("repo-test-slug")
            .name("Repo Test")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build();
        tenantRepository.save(tenant);

        assertThat(tenantRepository.findBySlug("repo-test-slug")).isPresent();
    }

    @Test
    void slugIsUnique() {
        Tenant first = Tenant.builder()
            .slug("dup-slug")
            .name("First")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build();
        tenantRepository.saveAndFlush(first);

        Tenant second = Tenant.builder()
            .slug("dup-slug")
            .name("Second")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build();

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> tenantRepository.saveAndFlush(second)
        );
    }

    @Test
    void findByIdempotencyKeyReturnsMatchingTenant() {
        Tenant tenant = Tenant.builder()
            .slug("idem-slug")
            .name("Idem Test")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .idempotencyKey("idem-key-1")
            .build();
        tenantRepository.save(tenant);

        assertThat(tenantRepository.findByIdempotencyKey("idem-key-1")).isPresent();
        assertThat(tenantRepository.findByIdempotencyKey("nonexistent")).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.domain.TenantTest" --tests "com.example.tnt_svc.domain.ProvisioningJobTest" --tests "com.example.tnt_svc.persistence.TenantRepositoryTest"`
Expected: FAIL — compile errors, none of `Tenant`/`ProvisioningJob`/`ProvisioningStep`/repositories exist yet.

- [ ] **Step 3: Write the exceptions**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/DuplicateSlugException.java
package com.example.tnt_svc.domain.exception;

public class DuplicateSlugException extends RuntimeException {
    public DuplicateSlugException(String slug) {
        super("Tenant slug already in use: " + slug);
    }
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/TenantNotFoundException.java
package com.example.tnt_svc.domain.exception;

import java.util.UUID;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(UUID id) {
        super("Tenant not found: " + id);
    }
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/ProvisioningJobNotFoundException.java
package com.example.tnt_svc.domain.exception;

import java.util.UUID;

public class ProvisioningJobNotFoundException extends RuntimeException {
    public ProvisioningJobNotFoundException(UUID id) {
        super("Provisioning job not found: " + id);
    }
}
```

- [ ] **Step 4: Write the enums**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/ProvisioningJobStatus.java
package com.example.tnt_svc.domain;

public enum ProvisioningJobStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    DEAD
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/ProvisioningStepStatus.java
package com.example.tnt_svc.domain;

public enum ProvisioningStepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
```

- [ ] **Step 5: Write the Tenant entity**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/Tenant.java
package com.example.tnt_svc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    private String region;

    @Column(name = "primary_owner_user_id", nullable = false)
    private UUID primaryOwnerUserId;

    @Column(name = "provisioning_job_id")
    private UUID provisioningJobId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @jakarta.persistence.PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void transitionTo(TenantStatus target) {
        TenantStateMachine.validateTransition(status, target);
        status = target;
    }

    public void activateAfterProvisioning() {
        transitionTo(TenantStatus.ACTIVE);
    }

    public void suspend() {
        transitionTo(TenantStatus.SUSPENDED);
    }

    public void reactivate() {
        transitionTo(TenantStatus.ACTIVE);
    }

    public void cancel() {
        transitionTo(TenantStatus.CANCELLED);
    }

    public void purge() {
        transitionTo(TenantStatus.PURGED);
    }
}
```

- [ ] **Step 6: Write the JSON converter and ProvisioningJob entity**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence/JsonMapConverter.java
package com.example.tnt_svc.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try {
            return MAPPER.writeValueAsString(attribute == null ? Map.of() : attribute);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize context to JSON", e);
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(dbData, MAP_TYPE);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize context JSON", e);
        }
    }
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/ProvisioningJob.java
package com.example.tnt_svc.domain;

import com.example.tnt_svc.persistence.JsonMapConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "provisioning_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProvisioningJobStatus status;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "callback_token", nullable = false)
    private String callbackToken;

    @Column(name = "last_error")
    private String lastError;

    @Convert(converter = JsonMapConverter.class)
    @Column(nullable = false, columnDefinition = "text")
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @jakarta.persistence.PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public void markInProgress() {
        status = ProvisioningJobStatus.IN_PROGRESS;
        startedAt = Instant.now();
    }

    public void markCompleted() {
        status = ProvisioningJobStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void markFailed(String error) {
        status = ProvisioningJobStatus.FAILED;
        lastError = error;
    }

    public void markDead() {
        status = ProvisioningJobStatus.DEAD;
        completedAt = Instant.now();
    }

    public boolean canRetry() {
        return status == ProvisioningJobStatus.FAILED && retryCount < maxRetries;
    }

    public void incrementRetry() {
        retryCount++;
    }

    public void mergeContext(Map<String, Object> updates) {
        Map<String, Object> merged = new HashMap<>(context);
        merged.putAll(updates);
        context = merged;
    }
}
```

- [ ] **Step 7: Write the ProvisioningStep entity**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/ProvisioningStep.java
package com.example.tnt_svc.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "provisioning_step")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvisioningStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProvisioningStepStatus status;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public void markInProgress() {
        status = ProvisioningStepStatus.IN_PROGRESS;
        startedAt = Instant.now();
    }

    public void markCompleted() {
        status = ProvisioningStepStatus.COMPLETED;
        completedAt = Instant.now();
    }

    public void markFailed(String error) {
        status = ProvisioningStepStatus.FAILED;
        errorMessage = error;
    }

    public void resetToPending() {
        status = ProvisioningStepStatus.PENDING;
        errorMessage = null;
        startedAt = null;
        completedAt = null;
    }
}
```

- [ ] **Step 8: Write the repositories**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence/TenantRepository.java
package com.example.tnt_svc.persistence;

import com.example.tnt_svc.domain.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByIdempotencyKey(String idempotencyKey);
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence/ProvisioningJobRepository.java
package com.example.tnt_svc.persistence;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProvisioningJobRepository extends JpaRepository<ProvisioningJob, UUID> {
    List<ProvisioningJob> findByStatus(ProvisioningJobStatus status);

    List<ProvisioningJob> findByStatusAndExpiresAtBefore(ProvisioningJobStatus status, Instant time);

    List<ProvisioningJob> findByStatusIn(List<ProvisioningJobStatus> statuses);
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence/ProvisioningStepRepository.java
package com.example.tnt_svc.persistence;

import com.example.tnt_svc.domain.ProvisioningStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProvisioningStepRepository extends JpaRepository<ProvisioningStep, UUID> {
    List<ProvisioningStep> findByJobIdOrderByStepOrderAsc(UUID jobId);

    Optional<ProvisioningStep> findByJobIdAndStepName(UUID jobId, String stepName);
}
```

- [ ] **Step 9: Write the Flyway migration**

```sql
-- gen-tnt-starter/src/main/resources/db/migration/gentnt/V1__tenant_and_provisioning.sql
CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    slug VARCHAR(63) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    region VARCHAR(64),
    primary_owner_user_id UUID NOT NULL,
    provisioning_job_id UUID,
    idempotency_key VARCHAR(255) UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE provisioning_job (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL,
    callback_token VARCHAR(255) NOT NULL,
    last_error TEXT,
    context TEXT NOT NULL DEFAULT '{}',
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_provisioning_job_status ON provisioning_job (status);
CREATE INDEX idx_provisioning_job_tenant_id ON provisioning_job (tenant_id);

CREATE TABLE provisioning_step (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_order INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX idx_provisioning_step_job_id ON provisioning_step (job_id);
```

- [ ] **Step 10: Run tests to verify they pass**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.domain.TenantTest" --tests "com.example.tnt_svc.domain.ProvisioningJobTest" --tests "com.example.tnt_svc.persistence.TenantRepositoryTest"`
Expected: PASS (12 tests). The repository test spins up a real Postgres Testcontainer and applies the Flyway migration automatically on context startup — takes longer than a pure unit test, that's expected.

- [ ] **Step 11: Commit**

```bash
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/domain gen-tnt-starter/src/main/java/com/example/tnt_svc/persistence gen-tnt-starter/src/main/resources/db gen-tnt-starter/src/test
git commit -m "feat: add Tenant/ProvisioningJob/ProvisioningStep entities, repositories, migration"
```

---

### Task 4: TenantService + TenantController + internal-secret gate

**Files:**

- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/service/TenantService.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/CreateTenantRequest.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/TenantResponse.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/ErrorResponse.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/TenantController.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/GlobalExceptionHandler.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/security/InternalSecretFilter.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/config/GenTntProperties.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/service/TenantServiceTest.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/web/TenantControllerTest.java`

**Interfaces:**

- Consumes: `Tenant`, `TenantRepository` (Task 3).
- Produces: `TenantService.createTenant(CreateTenantRequest): Tenant` (creates the tenant only — does NOT start the saga yet, that wiring is added in Task 8 once the orchestrator exists), `TenantService.getTenant(UUID): Tenant`, `.suspend(UUID): Tenant`, `.reactivate(UUID): Tenant`, `.cancel(UUID): Tenant`, `.purge(UUID): Tenant`. `GenTntProperties` (`@ConfigurationProperties(prefix = "gentnt")`, field `internalSecret`) — consumed by `InternalSecretFilter` here and by Task 6 (`ProvisioningStepsProperties` nests under the same prefix).

- [ ] **Step 1: Write the failing tests**

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/service/TenantServiceTest.java
package com.example.tnt_svc.service;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.domain.exception.DuplicateSlugException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.persistence.TenantRepository;
import com.example.tnt_svc.web.dto.CreateTenantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantServiceTest {

    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final TenantService tenantService = new TenantService(tenantRepository);

    @BeforeEach
    void stubSaveReturnsArgument() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createTenantPersistsWithProvisioningStatus() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.empty());
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme", "us", UUID.randomUUID(), null);

        Tenant tenant = tenantService.createTenant(request);

        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(tenant.getSlug()).isEqualTo("acme");
        verify(tenantRepository, times(1)).save(any(Tenant.class));
    }

    @Test
    void createTenantRejectsDuplicateSlug() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.of(mock(Tenant.class)));
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme", "us", UUID.randomUUID(), null);

        assertThatThrownBy(() -> tenantService.createTenant(request)).isInstanceOf(DuplicateSlugException.class);
    }

    @Test
    void createTenantWithSeenIdempotencyKeyReturnsExistingTenantWithoutSaving() {
        Tenant existing = Tenant.builder().slug("acme").idempotencyKey("key-1").status(TenantStatus.PROVISIONING).build();
        when(tenantRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme", "us", UUID.randomUUID(), "key-1");

        Tenant result = tenantService.createTenant(request);

        assertThat(result).isSameAs(existing);
        verify(tenantRepository, times(0)).save(any(Tenant.class));
    }

    @Test
    void getTenantThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(tenantRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tenantService.getTenant(id)).isInstanceOf(TenantNotFoundException.class);
    }

    @Test
    void suspendTransitionsActiveTenantToSuspended() {
        UUID id = UUID.randomUUID();
        Tenant tenant = Tenant.builder().slug("acme").status(TenantStatus.ACTIVE).build();
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));

        Tenant result = tenantService.suspend(id);

        assertThat(result.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }
}
```

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/web/TenantControllerTest.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.config.GenTntProperties;
import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.service.TenantService;
import com.example.tnt_svc.web.security.InternalSecretFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantControllerTest {

    private final TenantService tenantService = mock(TenantService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GenTntProperties properties = new GenTntProperties();
        properties.setInternalSecret("test-secret");
        mockMvc = MockMvcBuilders.standaloneSetup(new TenantController(tenantService))
            .addFilter(new InternalSecretFilter(properties))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void createTenantWithoutSecretHeaderIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createTenantWithSecretHeaderSucceeds() throws Exception {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).slug("acme").name("Acme").status(TenantStatus.PROVISIONING).build();
        when(tenantService.createTenant(any())).thenReturn(tenant);

        String body = new ObjectMapper().writeValueAsString(new com.example.tnt_svc.web.dto.CreateTenantRequest(
            "Acme", "acme", "us", UUID.randomUUID(), null));

        mockMvc.perform(post("/api/v1/tenants")
                .header("X-Internal-Secret", "test-secret")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isAccepted());
    }

    @Test
    void getTenantWithWrongSecretIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/" + UUID.randomUUID())
                .header("X-Internal-Secret", "wrong-secret"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.service.TenantServiceTest" --tests "com.example.tnt_svc.web.TenantControllerTest"`
Expected: FAIL — compile errors, `TenantService`/`TenantController`/`InternalSecretFilter`/`GenTntProperties`/DTOs don't exist yet.

- [ ] **Step 3: Add mockito dependency**

`gen-tnt-starter/src/main/java`... no file change needed — `spring-boot-starter-test` already pulls in Mockito transitively (confirmed same as `gen-auth-starter`'s test setup).

- [ ] **Step 4: Write GenTntProperties**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/config/GenTntProperties.java
package com.example.tnt_svc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gentnt")
public class GenTntProperties {

    private String internalSecret;

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(String internalSecret) {
        this.internalSecret = internalSecret;
    }
}
```

- [ ] **Step 5: Write the DTOs**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/CreateTenantRequest.java
package com.example.tnt_svc.web.dto;

import com.example.tnt_svc.validation.ValidSlug;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateTenantRequest(
    @NotBlank String name,
    @ValidSlug String slug,
    String region,
    @NotNull UUID primaryOwnerUserId,
    String idempotencyKey
) {
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/TenantResponse.java
package com.example.tnt_svc.web.dto;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
    UUID id,
    String slug,
    String name,
    TenantStatus status,
    String region,
    UUID primaryOwnerUserId,
    UUID provisioningJobId,
    Instant createdAt
) {
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(
            tenant.getId(),
            tenant.getSlug(),
            tenant.getName(),
            tenant.getStatus(),
            tenant.getRegion(),
            tenant.getPrimaryOwnerUserId(),
            tenant.getProvisioningJobId(),
            tenant.getCreatedAt()
        );
    }
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/ErrorResponse.java
package com.example.tnt_svc.web.dto;

public record ErrorResponse(String error, String message) {
}
```

- [ ] **Step 6: Write TenantService**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/service/TenantService.java
package com.example.tnt_svc.service;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.domain.exception.DuplicateSlugException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.persistence.TenantRepository;
import com.example.tnt_svc.web.dto.CreateTenantRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public Tenant createTenant(CreateTenantRequest request) {
        if (request.idempotencyKey() != null) {
            var existing = tenantRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (tenantRepository.findBySlug(request.slug()).isPresent()) {
            throw new DuplicateSlugException(request.slug());
        }

        Tenant tenant = Tenant.builder()
            .slug(request.slug())
            .name(request.name())
            .status(TenantStatus.PROVISIONING)
            .region(request.region())
            .primaryOwnerUserId(request.primaryOwnerUserId())
            .idempotencyKey(request.idempotencyKey())
            .build();

        return tenantRepository.save(tenant);
    }

    public Tenant getTenant(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> new TenantNotFoundException(id));
    }

    public Tenant suspend(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.suspend();
        return tenantRepository.save(tenant);
    }

    public Tenant reactivate(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.reactivate();
        return tenantRepository.save(tenant);
    }

    public Tenant cancel(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.cancel();
        return tenantRepository.save(tenant);
    }

    public Tenant purge(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.purge();
        return tenantRepository.save(tenant);
    }
}
```

- [ ] **Step 7: Write InternalSecretFilter**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/security/InternalSecretFilter.java
package com.example.tnt_svc.web.security;

import com.example.tnt_svc.config.GenTntProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Gates every /api/v1/** and /internal/** request behind a shared secret header. */
@Component
public class InternalSecretFilter extends OncePerRequestFilter {

    private final GenTntProperties properties;

    public InternalSecretFilter(GenTntProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean gated = path.startsWith("/api/v1/") || path.startsWith("/internal/");

        if (gated) {
            String provided = request.getHeader("X-Internal-Secret");
            if (properties.getInternalSecret() == null || !properties.getInternalSecret().equals(provided)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 8: Write GlobalExceptionHandler**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/GlobalExceptionHandler.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.domain.exception.DuplicateSlugException;
import com.example.tnt_svc.domain.exception.InvalidTenantStateTransitionException;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotFoundException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.web.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateSlugException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSlug(DuplicateSlugException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("duplicate_slug", e.getMessage()));
    }

    @ExceptionHandler(InvalidTenantStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidTenantStateTransitionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("invalid_state_transition", e.getMessage()));
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTenantNotFound(TenantNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("tenant_not_found", e.getMessage()));
    }

    @ExceptionHandler(ProvisioningJobNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleJobNotFound(ProvisioningJobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("job_not_found", e.getMessage()));
    }
}
```

- [ ] **Step 9: Write TenantController**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/TenantController.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.service.TenantService;
import com.example.tnt_svc.web.dto.CreateTenantRequest;
import com.example.tnt_svc.web.dto.TenantResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tenants")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    public ResponseEntity<TenantResponse> create(@Valid @RequestBody CreateTenantRequest request) {
        Tenant tenant = tenantService.createTenant(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(TenantResponse.from(tenant));
    }

    @GetMapping("/{id}")
    public TenantResponse get(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.getTenant(id));
    }

    @PatchMapping("/{id}/suspend")
    public TenantResponse suspend(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.suspend(id));
    }

    @PatchMapping("/{id}/reactivate")
    public TenantResponse reactivate(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.reactivate(id));
    }

    @PatchMapping("/{id}/cancel")
    public TenantResponse cancel(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.cancel(id));
    }

    @PatchMapping("/{id}/purge")
    public TenantResponse purge(@PathVariable UUID id) {
        return TenantResponse.from(tenantService.purge(id));
    }
}
```

- [ ] **Step 10: Run tests to verify they pass**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.service.TenantServiceTest" --tests "com.example.tnt_svc.web.TenantControllerTest"`
Expected: PASS (9 tests)

- [ ] **Step 11: Commit**

```bash
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/service gen-tnt-starter/src/main/java/com/example/tnt_svc/web gen-tnt-starter/src/main/java/com/example/tnt_svc/config gen-tnt-starter/src/test
git commit -m "feat: add TenantService, TenantController, internal-secret gate"
```

---

### Task 5: Step pipeline config + webhook client

**Files:**

- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/StepMode.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningStepDefinition.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningStepsProperties.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/StepCallResult.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/StepWebhookClient.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningStepsPropertiesTest.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/StepWebhookClientTest.java`

**Interfaces:**

- Produces: `ProvisioningStepDefinition(String name, String url, StepMode mode, boolean retryable, String compensateUrl)` record, `ProvisioningStepsProperties` (`@ConfigurationProperties(prefix = "gentnt.provisioning")`: `List<ProvisioningStepDefinition> steps`, `int maxRetries`, `int timeoutMinutes`, `long retrySchedulerIntervalMs`, `long timeoutSchedulerIntervalMs`), `StepCallResult(boolean success, Map<String,Object> context, String error)` record, `StepWebhookClient.call(ProvisioningStepDefinition, Map<String,Object> payload): StepCallResult`, `StepWebhookClient.compensate(ProvisioningStepDefinition, Map<String,Object> payload): void` (never throws — logs and swallows). Consumed by Task 6 (`ProvisioningSagaOrchestrator`).

- [ ] **Step 1: Write the failing tests**

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningStepsPropertiesTest.java
package com.example.tnt_svc.saga;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningStepsPropertiesTest {

    @Test
    void bindsStepListFromFlatProperties() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("gentnt.provisioning.max-retries", "3");
        source.put("gentnt.provisioning.timeout-minutes", "10");
        source.put("gentnt.provisioning.steps[0].name", "SCHEMA_BOOTSTRAP");
        source.put("gentnt.provisioning.steps[0].url", "http://localhost:9001/step");
        source.put("gentnt.provisioning.steps[0].mode", "SYNC");
        source.put("gentnt.provisioning.steps[0].retryable", "true");
        source.put("gentnt.provisioning.steps[1].name", "AUTH_BOOTSTRAP");
        source.put("gentnt.provisioning.steps[1].url", "http://localhost:9002/step");
        source.put("gentnt.provisioning.steps[1].mode", "ASYNC");
        source.put("gentnt.provisioning.steps[1].retryable", "true");
        source.put("gentnt.provisioning.steps[1].compensate-url", "http://localhost:9002/compensate");

        ConfigurationPropertySource propertySource = new MapConfigurationPropertySource(source);
        ProvisioningStepsProperties properties = new Binder(propertySource)
            .bind("gentnt.provisioning", ProvisioningStepsProperties.class)
            .get();

        assertThat(properties.getMaxRetries()).isEqualTo(3);
        assertThat(properties.getTimeoutMinutes()).isEqualTo(10);
        assertThat(properties.getSteps()).hasSize(2);
        assertThat(properties.getSteps().get(0).name()).isEqualTo("SCHEMA_BOOTSTRAP");
        assertThat(properties.getSteps().get(0).mode()).isEqualTo(StepMode.SYNC);
        assertThat(properties.getSteps().get(1).mode()).isEqualTo(StepMode.ASYNC);
        assertThat(properties.getSteps().get(1).compensateUrl()).isEqualTo("http://localhost:9002/compensate");
    }
}
```

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/StepWebhookClientTest.java
package com.example.tnt_svc.saga;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class StepWebhookClientTest {

    // One static server shared across all test methods in this class — reset
    // stubs before each test so a prior test's mapping for the same path
    // (callReturnsFailureOnNon2xx and callReturnsSuccessOn2xx both stub
    // POST /step) can't leak into a later test and flip its result.
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    private final StepWebhookClient client = new StepWebhookClient(RestClient.builder());

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    @Test
    void callReturnsSuccessOn2xx() {
        // WireMock defaults an unset response Content-Type to
        // application/octet-stream — RestClient's body(Map.class) correctly
        // refuses to JSON-decode that, so the header must be explicit here.
        wireMock.stubFor(post(urlEqualTo("/step")).willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json").withBody("{\"ok\":true}")));
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", wireMock.baseUrl() + "/step", StepMode.SYNC, true, null);

        StepCallResult result = client.call(step, Map.of("tenantId", "abc"));

        assertThat(result.success()).isTrue();
    }

    @Test
    void callReturnsFailureOnNon2xx() {
        wireMock.stubFor(post(urlEqualTo("/step")).willReturn(aResponse().withStatus(500)));
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", wireMock.baseUrl() + "/step", StepMode.SYNC, true, null);

        StepCallResult result = client.call(step, Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotBlank();
    }

    @Test
    void callReturnsFailureOnConnectionError() {
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", "http://localhost:1/unreachable", StepMode.SYNC, true, null);

        StepCallResult result = client.call(step, Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).isNotBlank();
    }

    @Test
    void compensateNeverThrowsEvenOnConnectionError() {
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", "http://localhost:1/unreachable", StepMode.SYNC, true, "http://localhost:1/compensate");

        client.compensate(step, Map.of());
        // no exception = pass
    }

    @Test
    void compensateCallsConfiguredUrl() {
        wireMock.stubFor(post(urlEqualTo("/compensate")).willReturn(aResponse().withStatus(200)));
        ProvisioningStepDefinition step = new ProvisioningStepDefinition(
            "SCHEMA_BOOTSTRAP", wireMock.baseUrl() + "/step", StepMode.SYNC, true, wireMock.baseUrl() + "/compensate");

        client.compensate(step, Map.of());

        wireMock.verify(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(urlEqualTo("/compensate")));
    }
}
```

- [ ] **Step 2: Add WireMock test dependency**

Add to `gen-tnt-starter/build.gradle`'s `dependencies` block:

```groovy
    testImplementation 'org.wiremock:wiremock-standalone:3.9.2'
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.saga.ProvisioningStepsPropertiesTest" --tests "com.example.tnt_svc.saga.StepWebhookClientTest"`
Expected: FAIL — compile errors, `StepMode`/`ProvisioningStepDefinition`/`ProvisioningStepsProperties`/`StepCallResult`/`StepWebhookClient` don't exist yet.

- [ ] **Step 4: Write StepMode, ProvisioningStepDefinition, StepCallResult**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/StepMode.java
package com.example.tnt_svc.saga;

public enum StepMode {
    SYNC,
    ASYNC
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningStepDefinition.java
package com.example.tnt_svc.saga;

public record ProvisioningStepDefinition(
    String name,
    String url,
    StepMode mode,
    boolean retryable,
    String compensateUrl
) {
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/StepCallResult.java
package com.example.tnt_svc.saga;

import java.util.Map;

public record StepCallResult(boolean success, Map<String, Object> context, String error) {

    public static StepCallResult success(Map<String, Object> context) {
        return new StepCallResult(true, context, null);
    }

    public static StepCallResult failure(String error) {
        return new StepCallResult(false, Map.of(), error);
    }
}
```

- [ ] **Step 5: Write ProvisioningStepsProperties**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningStepsProperties.java
package com.example.tnt_svc.saga;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "gentnt.provisioning")
public class ProvisioningStepsProperties {

    private List<ProvisioningStepDefinition> steps = List.of();
    private int maxRetries = 3;
    private int timeoutMinutes = 10;
    private long retrySchedulerIntervalMs = 120000;
    private long timeoutSchedulerIntervalMs = 60000;

    public List<ProvisioningStepDefinition> getSteps() {
        return steps;
    }

    public void setSteps(List<ProvisioningStepDefinition> steps) {
        this.steps = steps;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public long getRetrySchedulerIntervalMs() {
        return retrySchedulerIntervalMs;
    }

    public void setRetrySchedulerIntervalMs(long retrySchedulerIntervalMs) {
        this.retrySchedulerIntervalMs = retrySchedulerIntervalMs;
    }

    public long getTimeoutSchedulerIntervalMs() {
        return timeoutSchedulerIntervalMs;
    }

    public void setTimeoutSchedulerIntervalMs(long timeoutSchedulerIntervalMs) {
        this.timeoutSchedulerIntervalMs = timeoutSchedulerIntervalMs;
    }
}
```

- [ ] **Step 6: Write StepWebhookClient**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/StepWebhookClient.java
package com.example.tnt_svc.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * POSTs to a configured step URL. Sync steps: the response decides success/fail
 * immediately. Async steps: any 2xx just means "accepted", not "done" — real
 * completion arrives later via the callback endpoint (see ProvisioningSagaOrchestrator).
 * A connection error on either mode is treated as an explicit failure, same as a
 * non-2xx response — never silence.
 */
@Component
public class StepWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(StepWebhookClient.class);

    private final RestClient restClient;

    public StepWebhookClient(RestClient.Builder restClientBuilder) {
        // ponytail: force HTTP/1.1 — the JDK HttpClient's default h2c upgrade
        // preflight trips up WireMock's Jetty server in tests (connection
        // reset/EOF on the very first request). Step webhooks are plain
        // internal HTTP/1.1 endpoints anyway, so there's no downside.
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_1_1)
            .build();
        this.restClient = restClientBuilder
            .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient))
            .build();
    }

    public StepCallResult call(ProvisioningStepDefinition step, Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restClient.post()
                .uri(step.url())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);
            return StepCallResult.success(body == null ? Map.of() : body);
        } catch (Exception e) {
            log.warn("Step call failed: step={} url={}", step.name(), step.url(), e);
            return StepCallResult.failure(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    public void compensate(ProvisioningStepDefinition step, Map<String, Object> payload) {
        if (step.compensateUrl() == null || step.compensateUrl().isBlank()) {
            return;
        }
        try {
            restClient.post()
                .uri(step.compensateUrl())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Compensate call failed, ignoring (best-effort): step={} url={}", step.name(), step.compensateUrl(), e);
        }
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.saga.ProvisioningStepsPropertiesTest" --tests "com.example.tnt_svc.saga.StepWebhookClientTest"`
Expected: PASS (6 tests)

- [ ] **Step 8: Commit**

```bash
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/saga gen-tnt-starter/build.gradle gen-tnt-starter/src/test
git commit -m "feat: add configurable step pipeline definition and webhook client"
```

---

### Task 6: Redis distributed lock service

**Files:**

- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/ProvisioningLockException.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningLockService.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningLockServiceTest.java`

**Interfaces:**

- Produces: `ProvisioningLockService.tryLock(UUID tenantId, Duration ttl): Optional<String>` (empty if already locked, else a lock token), `ProvisioningLockService.unlock(UUID tenantId, String token): void` (only unlocks if the token matches — no-op otherwise). Consumed by Task 7 (`ProvisioningSagaOrchestrator`).

- [ ] **Step 1: Write the failing test**

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningLockServiceTest.java
package com.example.tnt_svc.saga;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningLockServiceTest {

    static GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    static ProvisioningLockService lockService;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(config);
        connectionFactory.afterPropertiesSet();
        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        lockService = new ProvisioningLockService(redisTemplate);
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    @Test
    void tryLockSucceedsWhenUnlocked() {
        Optional<String> token = lockService.tryLock(UUID.randomUUID(), Duration.ofMinutes(1));
        assertThat(token).isPresent();
    }

    @Test
    void tryLockFailsWhenAlreadyLocked() {
        UUID tenantId = UUID.randomUUID();
        lockService.tryLock(tenantId, Duration.ofMinutes(1));

        Optional<String> second = lockService.tryLock(tenantId, Duration.ofMinutes(1));

        assertThat(second).isEmpty();
    }

    @Test
    void unlockWithCorrectTokenReleasesLock() {
        UUID tenantId = UUID.randomUUID();
        String token = lockService.tryLock(tenantId, Duration.ofMinutes(1)).orElseThrow();

        lockService.unlock(tenantId, token);

        assertThat(lockService.tryLock(tenantId, Duration.ofMinutes(1))).isPresent();
    }

    @Test
    void unlockWithWrongTokenDoesNotReleaseLock() {
        UUID tenantId = UUID.randomUUID();
        lockService.tryLock(tenantId, Duration.ofMinutes(1));

        lockService.unlock(tenantId, "wrong-token");

        assertThat(lockService.tryLock(tenantId, Duration.ofMinutes(1))).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.saga.ProvisioningLockServiceTest"`
Expected: FAIL — compile error, `ProvisioningLockService` doesn't exist yet.

- [ ] **Step 3: Write the exception and lock service**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/ProvisioningLockException.java
package com.example.tnt_svc.domain.exception;

import java.util.UUID;

public class ProvisioningLockException extends RuntimeException {
    public ProvisioningLockException(UUID tenantId) {
        super("Could not acquire provisioning lock for tenant: " + tenantId);
    }
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningLockService.java
package com.example.tnt_svc.saga;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SET NX PX for acquire, a compare-and-delete Lua script for release — standard Redis lock pattern. */
@Component
public class ProvisioningLockService {

    private static final String UNLOCK_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "return redis.call('del', KEYS[1]) " +
            "else return 0 end";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public ProvisioningLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    }

    private String lockKey(UUID tenantId) {
        return "gentnt:provisioning-lock:" + tenantId;
    }

    public Optional<String> tryLock(UUID tenantId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey(tenantId), token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    public void unlock(UUID tenantId, String token) {
        redisTemplate.execute(unlockScript, List.of(lockKey(tenantId)), token);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.saga.ProvisioningLockServiceTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningLockService.java gen-tnt-starter/src/main/java/com/example/tnt_svc/domain/exception/ProvisioningLockException.java gen-tnt-starter/src/test
git commit -m "feat: add Redis-backed per-tenant provisioning lock"
```

---

### Task 7: ProvisioningSagaOrchestrator — sequential driver + callback handling

**Files:**

- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningSagaOrchestrator.java`
- Modify: `gen-tnt-starter/src/main/java/com/example/tnt_svc/service/TenantService.java` (wire saga kickoff into `createTenant`)
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningSagaOrchestratorTest.java`

**Interfaces:**

- Consumes: `ProvisioningStepsProperties`, `StepWebhookClient`, `ProvisioningLockService` (Task 5-6), `TenantRepository`, `ProvisioningJobRepository`, `ProvisioningStepRepository` (Task 3).
- Produces: `ProvisioningSagaOrchestrator.startProvisioning(UUID tenantId): UUID` (returns the created job's id), `.handleCallback(UUID jobId, String stepName, String token, boolean success, Map<String,Object> context, String error): void`, `.retryProvisioning(UUID jobId): void`, `.handleTimeout(UUID jobId): void`. Consumed by Task 8 (`ProvisioningController` callback endpoint, `RetryRecoveryScheduler`, `ProvisioningTimeoutScheduler`) and by `TenantService.createTenant` (this task).

- [ ] **Step 1: Write the failing test**

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningSagaOrchestratorTest.java
package com.example.tnt_svc.saga;

import com.example.tnt_svc.AbstractIntegrationTest;
import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.domain.ProvisioningStepStatus;
import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import com.example.tnt_svc.persistence.ProvisioningStepRepository;
import com.example.tnt_svc.persistence.TenantRepository;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ProvisioningSagaOrchestratorTest.TestConfig.class)
class ProvisioningSagaOrchestratorTest extends AbstractIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance().build();

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedis(DynamicPropertyRegistry registry) {
        REDIS.start();
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("gentnt.provisioning.max-retries", () -> "3");
        registry.add("gentnt.provisioning.timeout-minutes", () -> "10");
    }

    @Autowired
    private ProvisioningSagaOrchestrator orchestrator;
    @Autowired
    private TenantRepository tenantRepository;
    @Autowired
    private ProvisioningJobRepository jobRepository;
    @Autowired
    private ProvisioningStepRepository stepRepository;
    @Autowired
    private ProvisioningStepsProperties stepsProperties;

    private Tenant tenant;

    @BeforeEach
    void setUpTenant() {
        tenant = tenantRepository.save(Tenant.builder()
            .slug("orch-test-" + UUID.randomUUID())
            .name("Orchestrator Test")
            .status(TenantStatus.PROVISIONING)
            .primaryOwnerUserId(UUID.randomUUID())
            .build());
    }

    @Test
    void allSyncStepsSucceedActivatesTenant() {
        wireMock.stubFor(post(urlEqualTo("/sync-step")).willReturn(aResponse().withStatus(200).withBody("{}")));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ONLY_STEP", wireMock.baseUrl() + "/sync-step", StepMode.SYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());

        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(ProvisioningJobStatus.COMPLETED);
        assertThat(tenantRepository.findById(tenant.getId()).orElseThrow().getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    void syncStepFailureMarksJobFailed() {
        wireMock.stubFor(post(urlEqualTo("/fail-step")).willReturn(aResponse().withStatus(500)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ONLY_STEP", wireMock.baseUrl() + "/fail-step", StepMode.SYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.FAILED);
    }

    @Test
    void asyncStepAdvancesOnSuccessfulCallback() {
        wireMock.stubFor(post(urlEqualTo("/async-step")).willReturn(aResponse().withStatus(202)));
        wireMock.stubFor(post(urlEqualTo("/sync-step-2")).willReturn(aResponse().withStatus(200).withBody("{}")));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-step", StepMode.ASYNC, true, null),
            new ProvisioningStepDefinition("SYNC_STEP_2", wireMock.baseUrl() + "/sync-step-2", StepMode.SYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        ProvisioningJob afterStart = jobRepository.findById(jobId).orElseThrow();
        assertThat(afterStart.getStatus()).isEqualTo(ProvisioningJobStatus.IN_PROGRESS);

        orchestrator.handleCallback(jobId, "ASYNC_STEP", afterStart.getCallbackToken(), true, Map.of("k", "v"), null);

        ProvisioningJob afterCallback = jobRepository.findById(jobId).orElseThrow();
        assertThat(afterCallback.getStatus()).isEqualTo(ProvisioningJobStatus.COMPLETED);
        assertThat(afterCallback.getContext()).containsEntry("k", "v");
    }

    @Test
    void callbackWithWrongTokenIsRejected() {
        wireMock.stubFor(post(urlEqualTo("/async-step2")).willReturn(aResponse().withStatus(202)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ASYNC_STEP", wireMock.baseUrl() + "/async-step2", StepMode.ASYNC, true, null)
        ));

        UUID jobId = orchestrator.startProvisioning(tenant.getId());

        orchestrator.handleCallback(jobId, "ASYNC_STEP", "wrong-token", true, Map.of(), null);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.IN_PROGRESS);
    }

    @Test
    void retryProvisioningMarksJobDeadWhenRetriesExhausted() {
        wireMock.stubFor(post(urlEqualTo("/always-fail")).willReturn(aResponse().withStatus(500)));
        stepsProperties.setSteps(List.of(
            new ProvisioningStepDefinition("ONLY_STEP", wireMock.baseUrl() + "/always-fail", StepMode.SYNC, true, null)
        ));
        stepsProperties.setMaxRetries(0);

        UUID jobId = orchestrator.startProvisioning(tenant.getId());
        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.FAILED);

        orchestrator.retryProvisioning(jobId);

        assertThat(jobRepository.findById(jobId).orElseThrow().getStatus()).isEqualTo(ProvisioningJobStatus.DEAD);
    }

    @TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.web.client.RestClient.Builder restClientBuilder() {
            return org.springframework.web.client.RestClient.builder();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.saga.ProvisioningSagaOrchestratorTest"`
Expected: FAIL — compile error, `ProvisioningSagaOrchestrator` doesn't exist yet.

- [ ] **Step 3: Write ProvisioningSagaOrchestrator**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningSagaOrchestrator.java
package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningStep;
import com.example.tnt_svc.domain.ProvisioningStepStatus;
import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotFoundException;
import com.example.tnt_svc.domain.exception.ProvisioningLockException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import com.example.tnt_svc.persistence.ProvisioningStepRepository;
import com.example.tnt_svc.persistence.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ProvisioningSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProvisioningSagaOrchestrator.class);
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final TenantRepository tenantRepository;
    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningStepRepository stepRepository;
    private final ProvisioningStepsProperties stepsProperties;
    private final StepWebhookClient webhookClient;
    private final ProvisioningLockService lockService;

    public ProvisioningSagaOrchestrator(
        TenantRepository tenantRepository,
        ProvisioningJobRepository jobRepository,
        ProvisioningStepRepository stepRepository,
        ProvisioningStepsProperties stepsProperties,
        StepWebhookClient webhookClient,
        ProvisioningLockService lockService
    ) {
        this.tenantRepository = tenantRepository;
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.stepsProperties = stepsProperties;
        this.webhookClient = webhookClient;
        this.lockService = lockService;
    }

    public UUID startProvisioning(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new TenantNotFoundException(tenantId));

        ProvisioningJob job = ProvisioningJob.builder()
            .tenantId(tenantId)
            .status(com.example.tnt_svc.domain.ProvisioningJobStatus.PENDING)
            .retryCount(0)
            .maxRetries(stepsProperties.getMaxRetries())
            .callbackToken(UUID.randomUUID().toString())
            .context(new HashMap<>())
            .expiresAt(Instant.now().plus(Duration.ofMinutes(stepsProperties.getTimeoutMinutes())))
            .build();
        job = jobRepository.save(job);

        int order = 0;
        for (ProvisioningStepDefinition def : stepsProperties.getSteps()) {
            stepRepository.save(ProvisioningStep.builder()
                .jobId(job.getId())
                .stepName(def.name())
                .stepOrder(order++)
                .status(ProvisioningStepStatus.PENDING)
                .build());
        }

        tenant.setProvisioningJobId(job.getId());
        tenantRepository.save(tenant);

        job.markInProgress();
        jobRepository.save(job);

        driveNextStep(job.getId());
        return job.getId();
    }

    public void driveNextStep(UUID jobId) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> new ProvisioningJobNotFoundException(jobId));
        List<ProvisioningStep> steps = stepRepository.findByJobIdOrderByStepOrderAsc(jobId);

        Optional<ProvisioningStep> next = steps.stream()
            .filter(s -> s.getStatus() != ProvisioningStepStatus.COMPLETED)
            .findFirst();

        if (next.isEmpty()) {
            completeJob(job);
            return;
        }

        ProvisioningStep step = next.get();
        ProvisioningStepDefinition definition = findDefinition(step.getStepName());

        Optional<String> lockToken = lockService.tryLock(job.getTenantId(), LOCK_TTL);
        if (lockToken.isEmpty()) {
            log.debug("Skipping step drive, tenant lock already held: tenantId={}", job.getTenantId());
            return;
        }

        try {
            step.markInProgress();
            stepRepository.save(step);

            Map<String, Object> payload = new HashMap<>(job.getContext());
            payload.put("tenantId", job.getTenantId());
            payload.put("jobId", job.getId());
            payload.put("stepName", step.getStepName());
            if (definition.mode() == StepMode.ASYNC) {
                payload.put("callbackToken", job.getCallbackToken());
            }

            StepCallResult result = webhookClient.call(definition, payload);

            if (!result.success()) {
                step.markFailed(result.error());
                stepRepository.save(step);
                job.markFailed("Step " + step.getStepName() + " failed: " + result.error());
                jobRepository.save(job);
                return;
            }

            if (definition.mode() == StepMode.SYNC) {
                step.markCompleted();
                stepRepository.save(step);
                job.mergeContext(result.context());
                jobRepository.save(job);
                lockService.unlock(job.getTenantId(), lockToken.get());
                driveNextStep(jobId);
                return;
            }
            // async: stays IN_PROGRESS, real completion arrives via handleCallback
        } finally {
            lockService.unlock(job.getTenantId(), lockToken.get());
        }
    }

    public void handleCallback(UUID jobId, String stepName, String token, boolean success, Map<String, Object> context, String error) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> new ProvisioningJobNotFoundException(jobId));

        if (!job.getCallbackToken().equals(token)) {
            log.warn("Rejected provisioning callback with mismatched token: jobId={} stepName={}", jobId, stepName);
            return;
        }

        ProvisioningStep step = stepRepository.findByJobIdAndStepName(jobId, stepName).orElse(null);
        if (step == null || step.getStatus() != ProvisioningStepStatus.IN_PROGRESS) {
            log.warn("Rejected provisioning callback for unknown/non-in-progress step: jobId={} stepName={}", jobId, stepName);
            return;
        }

        if (success) {
            step.markCompleted();
            stepRepository.save(step);
            job.mergeContext(context == null ? Map.of() : context);
            jobRepository.save(job);
            driveNextStep(jobId);
        } else {
            step.markFailed(error);
            stepRepository.save(step);
            job.markFailed("Step " + stepName + " failed: " + error);
            jobRepository.save(job);
        }
    }

    public void retryProvisioning(UUID jobId) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> new ProvisioningJobNotFoundException(jobId));
        if (!job.canRetry()) {
            job.markDead();
            jobRepository.save(job);
            return;
        }

        job.incrementRetry();
        job.markInProgress();
        jobRepository.save(job);

        stepRepository.findByJobIdOrderByStepOrderAsc(jobId).stream()
            .filter(s -> s.getStatus() == ProvisioningStepStatus.FAILED)
            .forEach(s -> {
                s.resetToPending();
                stepRepository.save(s);
            });

        driveNextStep(jobId);
    }

    public void handleTimeout(UUID jobId) {
        ProvisioningJob job = jobRepository.findById(jobId).orElseThrow(() -> new ProvisioningJobNotFoundException(jobId));

        List<ProvisioningStep> completedStepsReversed = stepRepository.findByJobIdOrderByStepOrderAsc(jobId).stream()
            .filter(s -> s.getStatus() == ProvisioningStepStatus.COMPLETED)
            .sorted((a, b) -> Integer.compare(b.getStepOrder(), a.getStepOrder()))
            .toList();

        for (ProvisioningStep step : completedStepsReversed) {
            ProvisioningStepDefinition definition = findDefinition(step.getStepName());
            Map<String, Object> payload = new HashMap<>(job.getContext());
            payload.put("tenantId", job.getTenantId());
            payload.put("jobId", job.getId());
            payload.put("stepName", step.getStepName());
            webhookClient.compensate(definition, payload);
        }

        job.markDead();
        jobRepository.save(job);
    }

    private void completeJob(ProvisioningJob job) {
        job.markCompleted();
        jobRepository.save(job);

        Tenant tenant = tenantRepository.findById(job.getTenantId()).orElseThrow(() -> new TenantNotFoundException(job.getTenantId()));
        tenant.activateAfterProvisioning();
        tenantRepository.save(tenant);
    }

    private ProvisioningStepDefinition findDefinition(String stepName) {
        return stepsProperties.getSteps().stream()
            .filter(d -> d.name().equals(stepName))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No step definition configured for: " + stepName));
    }
}
```

- [ ] **Step 4: Wire the orchestrator into TenantService**

Modify `gen-tnt-starter/src/main/java/com/example/tnt_svc/service/TenantService.java`:

```java
// Replace the constructor and createTenant method
package com.example.tnt_svc.service;

import com.example.tnt_svc.domain.Tenant;
import com.example.tnt_svc.domain.TenantStatus;
import com.example.tnt_svc.domain.exception.DuplicateSlugException;
import com.example.tnt_svc.domain.exception.TenantNotFoundException;
import com.example.tnt_svc.persistence.TenantRepository;
import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import com.example.tnt_svc.web.dto.CreateTenantRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TenantService {

    private final TenantRepository tenantRepository;
    private final ProvisioningSagaOrchestrator orchestrator;

    public TenantService(TenantRepository tenantRepository, ProvisioningSagaOrchestrator orchestrator) {
        this.tenantRepository = tenantRepository;
        this.orchestrator = orchestrator;
    }

    public Tenant createTenant(CreateTenantRequest request) {
        if (request.idempotencyKey() != null) {
            var existing = tenantRepository.findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        if (tenantRepository.findBySlug(request.slug()).isPresent()) {
            throw new DuplicateSlugException(request.slug());
        }

        Tenant tenant = Tenant.builder()
            .slug(request.slug())
            .name(request.name())
            .status(TenantStatus.PROVISIONING)
            .region(request.region())
            .primaryOwnerUserId(request.primaryOwnerUserId())
            .idempotencyKey(request.idempotencyKey())
            .build();

        tenant = tenantRepository.save(tenant);
        orchestrator.startProvisioning(tenant.getId());
        return tenantRepository.findById(tenant.getId()).orElseThrow();
    }

    public Tenant getTenant(UUID id) {
        return tenantRepository.findById(id).orElseThrow(() -> new TenantNotFoundException(id));
    }

    public Tenant suspend(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.suspend();
        return tenantRepository.save(tenant);
    }

    public Tenant reactivate(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.reactivate();
        return tenantRepository.save(tenant);
    }

    public Tenant cancel(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.cancel();
        return tenantRepository.save(tenant);
    }

    public Tenant purge(UUID id) {
        Tenant tenant = getTenant(id);
        tenant.purge();
        return tenantRepository.save(tenant);
    }
}
```

Update `TenantServiceTest`'s constructor calls to pass a mocked `ProvisioningSagaOrchestrator`:

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/service/TenantServiceTest.java
// Change the field declarations at the top of the class to:
    private final TenantRepository tenantRepository = mock(TenantRepository.class);
    private final com.example.tnt_svc.saga.ProvisioningSagaOrchestrator orchestrator =
        mock(com.example.tnt_svc.saga.ProvisioningSagaOrchestrator.class);
    private final TenantService tenantService = new TenantService(tenantRepository, orchestrator);
```

Update `@BeforeEach stubSaveReturnsArgument` to also stub `findById` so the post-orchestrator re-fetch in `createTenant` works:

```java
    @BeforeEach
    void stubSaveReturnsArgument() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            when(tenantRepository.findById(t.getId() == null ? any() : t.getId())).thenReturn(Optional.of(t));
            return t;
        });
    }
```

Also update the `createTenantPersistsWithProvisioningStatus` test to stub `findById` explicitly since Mockito's `any()` inside a stub answer above doesn't reliably match a concrete UUID — replace that whole test with:

```java
    @Test
    void createTenantPersistsWithProvisioningStatus() {
        when(tenantRepository.findBySlug("acme")).thenReturn(Optional.empty());
        CreateTenantRequest request = new CreateTenantRequest("Acme Corp", "acme", "us", UUID.randomUUID(), null);

        UUID generatedId = UUID.randomUUID();
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> {
            Tenant t = inv.getArgument(0);
            t.setId(generatedId);
            return t;
        });
        when(tenantRepository.findById(generatedId)).thenAnswer(inv ->
            Optional.of(Tenant.builder().id(generatedId).slug("acme").status(TenantStatus.PROVISIONING).build()));

        Tenant tenant = tenantService.createTenant(request);

        assertThat(tenant.getStatus()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(tenant.getSlug()).isEqualTo("acme");
        verify(tenantRepository, times(1)).save(any(Tenant.class));
        verify(orchestrator, times(1)).startProvisioning(generatedId);
    }
```

Remove the now-redundant generic `@BeforeEach stubSaveReturnsArgument` method entirely (each test that needs `save` stubs it explicitly now) — delete it and its `@BeforeEach` import usage if no other test needs it. Check `createTenantWithSeenIdempotencyKeyReturnsExistingTenantWithoutSaving` and `suspendTransitionsActiveTenantToSuspended` still pass without it (they use `save` via `when(...).thenAnswer(inv -> inv.getArgument(0))` implicitly through Mockito's default mock behavior returning `null` — actually `suspend()`/etc. call `tenantRepository.save(tenant)` and return its result, so those tests need their own explicit `save` stub too). Add to each:

```java
    @Test
    void suspendTransitionsActiveTenantToSuspended() {
        UUID id = UUID.randomUUID();
        Tenant tenant = Tenant.builder().slug("acme").status(TenantStatus.ACTIVE).build();
        when(tenantRepository.findById(id)).thenReturn(Optional.of(tenant));
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

        Tenant result = tenantService.suspend(id);

        assertThat(result.getStatus()).isEqualTo(TenantStatus.SUSPENDED);
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.saga.ProvisioningSagaOrchestratorTest" --tests "com.example.tnt_svc.service.TenantServiceTest"`
Expected: PASS (10 tests)

- [ ] **Step 6: Run the full test suite**

Run: `./gradlew.bat :gen-tnt-starter:test`
Expected: all tests PASS, no regressions in earlier tasks' tests.

- [ ] **Step 7: Commit**

```bash
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningSagaOrchestrator.java gen-tnt-starter/src/main/java/com/example/tnt_svc/service/TenantService.java gen-tnt-starter/src/test
git commit -m "feat: add ProvisioningSagaOrchestrator, wire saga kickoff into TenantService"
```

---

### Task 8: Provisioning callback endpoint + introspection controller

**Files:**

- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/service/ProvisioningService.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/ProvisioningJobResponse.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/ProvisioningStepResponse.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/StepCallbackRequest.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/web/ProvisioningController.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/web/ProvisioningControllerTest.java`

**Interfaces:**

- Consumes: `ProvisioningSagaOrchestrator` (Task 7), `ProvisioningJobRepository`/`ProvisioningStepRepository` (Task 3).
- Produces: `ProvisioningService.getJob(UUID): ProvisioningJob`, `.getSteps(UUID jobId): List<ProvisioningStep>`, `.manualRetry(UUID jobId): void`, `.listFailed(): List<ProvisioningJob>`. HTTP: `GET /api/v1/provisioning/jobs/{id}`, `GET /api/v1/provisioning/jobs/{id}/steps`, `POST /api/v1/provisioning/jobs/{id}/retry`, `GET /api/v1/provisioning/failed`, `POST /internal/provisioning/jobs/{jobId}/steps/{stepName}/callback`.

- [ ] **Step 1: Write the failing test**

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/web/ProvisioningControllerTest.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.config.GenTntProperties;
import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import com.example.tnt_svc.service.ProvisioningService;
import com.example.tnt_svc.web.security.InternalSecretFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProvisioningControllerTest {

    private final ProvisioningService provisioningService = mock(ProvisioningService.class);
    private final ProvisioningSagaOrchestrator orchestrator = mock(ProvisioningSagaOrchestrator.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        GenTntProperties properties = new GenTntProperties();
        properties.setInternalSecret("test-secret");
        mockMvc = MockMvcBuilders.standaloneSetup(new ProvisioningController(provisioningService, orchestrator))
            .addFilter(new InternalSecretFilter(properties))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void getJobRequiresSecret() throws Exception {
        mockMvc.perform(get("/api/v1/provisioning/jobs/" + UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getJobReturnsJob() throws Exception {
        UUID jobId = UUID.randomUUID();
        ProvisioningJob job = ProvisioningJob.builder().id(jobId).tenantId(UUID.randomUUID())
            .status(ProvisioningJobStatus.IN_PROGRESS).retryCount(0).maxRetries(3)
            .callbackToken("tok").context(java.util.Map.of()).build();
        when(provisioningService.getJob(jobId)).thenReturn(job);

        mockMvc.perform(get("/api/v1/provisioning/jobs/" + jobId).header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isOk());
    }

    @Test
    void listFailedReturnsList() throws Exception {
        when(provisioningService.listFailed()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/provisioning/failed").header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isOk());
    }

    @Test
    void retryTriggersManualRetry() throws Exception {
        UUID jobId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/provisioning/jobs/" + jobId + "/retry").header("X-Internal-Secret", "test-secret"))
            .andExpect(status().isAccepted());

        verify(provisioningService, times(1)).manualRetry(jobId);
    }

    @Test
    void callbackRequiresInternalSecretToo() throws Exception {
        mockMvc.perform(post("/internal/provisioning/jobs/" + UUID.randomUUID() + "/steps/STEP_A/callback")
                .contentType("application/json")
                .content("{\"success\":true,\"token\":\"tok\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void callbackDelegatesToOrchestrator() throws Exception {
        UUID jobId = UUID.randomUUID();
        String body = new ObjectMapper().writeValueAsString(
            new com.example.tnt_svc.web.dto.StepCallbackRequest("tok", true, java.util.Map.of("k", "v"), null));

        mockMvc.perform(post("/internal/provisioning/jobs/" + jobId + "/steps/STEP_A/callback")
                .header("X-Internal-Secret", "test-secret")
                .contentType("application/json")
                .content(body))
            .andExpect(status().isOk());

        verify(orchestrator, times(1)).handleCallback(eq(jobId), eq("STEP_A"), eq("tok"), eq(true), any(), eq((String) null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.web.ProvisioningControllerTest"`
Expected: FAIL — compile errors, `ProvisioningService`/`ProvisioningController`/DTOs don't exist yet.

- [ ] **Step 3: Write the DTOs**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/ProvisioningJobResponse.java
package com.example.tnt_svc.web.dto;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;

import java.time.Instant;
import java.util.UUID;

public record ProvisioningJobResponse(
    UUID id,
    UUID tenantId,
    ProvisioningJobStatus status,
    int retryCount,
    String lastError,
    Instant startedAt,
    Instant completedAt,
    Instant expiresAt
) {
    public static ProvisioningJobResponse from(ProvisioningJob job) {
        return new ProvisioningJobResponse(
            job.getId(), job.getTenantId(), job.getStatus(), job.getRetryCount(),
            job.getLastError(), job.getStartedAt(), job.getCompletedAt(), job.getExpiresAt());
    }
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/ProvisioningStepResponse.java
package com.example.tnt_svc.web.dto;

import com.example.tnt_svc.domain.ProvisioningStep;
import com.example.tnt_svc.domain.ProvisioningStepStatus;

import java.util.UUID;

public record ProvisioningStepResponse(
    UUID id,
    String stepName,
    int stepOrder,
    ProvisioningStepStatus status,
    int retryCount,
    String errorMessage
) {
    public static ProvisioningStepResponse from(ProvisioningStep step) {
        return new ProvisioningStepResponse(
            step.getId(), step.getStepName(), step.getStepOrder(), step.getStatus(),
            step.getRetryCount(), step.getErrorMessage());
    }
}
```

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/dto/StepCallbackRequest.java
package com.example.tnt_svc.web.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

public record StepCallbackRequest(
    @NotBlank String token,
    boolean success,
    Map<String, Object> context,
    String error
) {
}
```

- [ ] **Step 4: Write ProvisioningService**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/service/ProvisioningService.java
package com.example.tnt_svc.service;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.domain.ProvisioningStep;
import com.example.tnt_svc.domain.exception.ProvisioningJobNotFoundException;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import com.example.tnt_svc.persistence.ProvisioningStepRepository;
import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ProvisioningService {

    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningStepRepository stepRepository;
    private final ProvisioningSagaOrchestrator orchestrator;

    public ProvisioningService(
        ProvisioningJobRepository jobRepository,
        ProvisioningStepRepository stepRepository,
        ProvisioningSagaOrchestrator orchestrator
    ) {
        this.jobRepository = jobRepository;
        this.stepRepository = stepRepository;
        this.orchestrator = orchestrator;
    }

    public ProvisioningJob getJob(UUID id) {
        return jobRepository.findById(id).orElseThrow(() -> new ProvisioningJobNotFoundException(id));
    }

    public List<ProvisioningStep> getSteps(UUID jobId) {
        return stepRepository.findByJobIdOrderByStepOrderAsc(jobId);
    }

    public void manualRetry(UUID jobId) {
        orchestrator.retryProvisioning(jobId);
    }

    public List<ProvisioningJob> listFailed() {
        return jobRepository.findByStatusIn(List.of(ProvisioningJobStatus.FAILED, ProvisioningJobStatus.DEAD));
    }
}
```

- [ ] **Step 5: Write ProvisioningController**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/web/ProvisioningController.java
package com.example.tnt_svc.web;

import com.example.tnt_svc.saga.ProvisioningSagaOrchestrator;
import com.example.tnt_svc.service.ProvisioningService;
import com.example.tnt_svc.web.dto.ProvisioningJobResponse;
import com.example.tnt_svc.web.dto.ProvisioningStepResponse;
import com.example.tnt_svc.web.dto.StepCallbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class ProvisioningController {

    private final ProvisioningService provisioningService;
    private final ProvisioningSagaOrchestrator orchestrator;

    public ProvisioningController(ProvisioningService provisioningService, ProvisioningSagaOrchestrator orchestrator) {
        this.provisioningService = provisioningService;
        this.orchestrator = orchestrator;
    }

    @GetMapping("/api/v1/provisioning/jobs/{id}")
    public ProvisioningJobResponse getJob(@PathVariable UUID id) {
        return ProvisioningJobResponse.from(provisioningService.getJob(id));
    }

    @GetMapping("/api/v1/provisioning/jobs/{id}/steps")
    public List<ProvisioningStepResponse> getSteps(@PathVariable UUID id) {
        return provisioningService.getSteps(id).stream().map(ProvisioningStepResponse::from).toList();
    }

    @PostMapping("/api/v1/provisioning/jobs/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable UUID id) {
        provisioningService.manualRetry(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/api/v1/provisioning/failed")
    public List<ProvisioningJobResponse> listFailed() {
        return provisioningService.listFailed().stream().map(ProvisioningJobResponse::from).toList();
    }

    @PostMapping("/internal/provisioning/jobs/{jobId}/steps/{stepName}/callback")
    public ResponseEntity<Void> callback(
        @PathVariable UUID jobId,
        @PathVariable String stepName,
        @Valid @RequestBody StepCallbackRequest request
    ) {
        orchestrator.handleCallback(jobId, stepName, request.token(), request.success(), request.context(), request.error());
        return ResponseEntity.ok().build();
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.web.ProvisioningControllerTest"`
Expected: PASS (6 tests)

- [ ] **Step 7: Commit**

```bash
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/service/ProvisioningService.java gen-tnt-starter/src/main/java/com/example/tnt_svc/web gen-tnt-starter/src/test
git commit -m "feat: add provisioning introspection endpoints and step callback endpoint"
```

---

### Task 9: Retry and timeout schedulers

**Files:**

- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/RetryRecoveryScheduler.java`
- Create: `gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningTimeoutScheduler.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/RetryRecoverySchedulerTest.java`
- Test: `gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningTimeoutSchedulerTest.java`

**Interfaces:**

- Consumes: `ProvisioningJobRepository` (Task 3), `ProvisioningSagaOrchestrator` (Task 7), `ProvisioningStepsProperties` (Task 5).
- Produces: `RetryRecoveryScheduler.recoverFailedJobs(): void` (also `@Scheduled`), `ProvisioningTimeoutScheduler.handleExpiredJobs(): void` (also `@Scheduled`). Both are terminal for this plan — no later task consumes them directly, they're driven by Spring's scheduler at runtime.

- [ ] **Step 1: Write the failing tests**

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/RetryRecoverySchedulerTest.java
package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetryRecoverySchedulerTest {

    private final ProvisioningJobRepository jobRepository = mock(ProvisioningJobRepository.class);
    private final ProvisioningSagaOrchestrator orchestrator = mock(ProvisioningSagaOrchestrator.class);
    private final RetryRecoveryScheduler scheduler = new RetryRecoveryScheduler(jobRepository, orchestrator);

    // The scheduler itself doesn't decide retry-vs-dead — it just hands every
    // FAILED job to the orchestrator, which re-fetches fresh state and makes
    // that call internally (see ProvisioningSagaOrchestratorTest for the
    // retries-exhausted-marks-dead case). Two jobs here only to confirm the
    // scheduler loops over all of them, not just the first.
    @Test
    void handsEveryFailedJobToTheOrchestrator() {
        UUID jobId1 = UUID.randomUUID();
        UUID jobId2 = UUID.randomUUID();
        ProvisioningJob job1 = ProvisioningJob.builder()
            .id(jobId1).tenantId(UUID.randomUUID()).status(ProvisioningJobStatus.FAILED)
            .retryCount(1).maxRetries(3).callbackToken("tok").context(Map.of()).build();
        ProvisioningJob job2 = ProvisioningJob.builder()
            .id(jobId2).tenantId(UUID.randomUUID()).status(ProvisioningJobStatus.FAILED)
            .retryCount(3).maxRetries(3).callbackToken("tok").context(Map.of()).build();
        when(jobRepository.findByStatus(ProvisioningJobStatus.FAILED)).thenReturn(List.of(job1, job2));

        scheduler.recoverFailedJobs();

        verify(orchestrator, times(1)).retryProvisioning(jobId1);
        verify(orchestrator, times(1)).retryProvisioning(jobId2);
    }
}
```

```java
// gen-tnt-starter/src/test/java/com/example/tnt_svc/saga/ProvisioningTimeoutSchedulerTest.java
package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProvisioningTimeoutSchedulerTest {

    private final ProvisioningJobRepository jobRepository = mock(ProvisioningJobRepository.class);
    private final ProvisioningSagaOrchestrator orchestrator = mock(ProvisioningSagaOrchestrator.class);
    private final ProvisioningTimeoutScheduler scheduler = new ProvisioningTimeoutScheduler(jobRepository, orchestrator);

    @Test
    void handlesExpiredInProgressJobs() {
        UUID jobId = UUID.randomUUID();
        ProvisioningJob job = ProvisioningJob.builder()
            .id(jobId).tenantId(UUID.randomUUID()).status(ProvisioningJobStatus.IN_PROGRESS)
            .retryCount(0).maxRetries(3).callbackToken("tok").context(Map.of())
            .expiresAt(Instant.now().minusSeconds(60)).build();
        when(jobRepository.findByStatusAndExpiresAtBefore(eq(ProvisioningJobStatus.IN_PROGRESS), any())).thenReturn(List.of(job));

        scheduler.handleExpiredJobs();

        verify(orchestrator, times(1)).handleTimeout(jobId);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.saga.RetryRecoverySchedulerTest" --tests "com.example.tnt_svc.saga.ProvisioningTimeoutSchedulerTest"`
Expected: FAIL — compile errors, `RetryRecoveryScheduler`/`ProvisioningTimeoutScheduler` don't exist yet.

- [ ] **Step 3: Write RetryRecoveryScheduler**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/RetryRecoveryScheduler.java
package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetryRecoveryScheduler {

    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningSagaOrchestrator orchestrator;

    public RetryRecoveryScheduler(ProvisioningJobRepository jobRepository, ProvisioningSagaOrchestrator orchestrator) {
        this.jobRepository = jobRepository;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${gentnt.provisioning.retry-scheduler-interval-ms:120000}")
    public void recoverFailedJobs() {
        // retryProvisioning() itself checks canRetry() and marks the job DEAD
        // when it's false — no branching needed here, every FAILED job just
        // gets handed to the orchestrator.
        for (ProvisioningJob job : jobRepository.findByStatus(ProvisioningJobStatus.FAILED)) {
            orchestrator.retryProvisioning(job.getId());
        }
    }
}
```

- [ ] **Step 5: Write ProvisioningTimeoutScheduler**

```java
// gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningTimeoutScheduler.java
package com.example.tnt_svc.saga;

import com.example.tnt_svc.domain.ProvisioningJob;
import com.example.tnt_svc.domain.ProvisioningJobStatus;
import com.example.tnt_svc.persistence.ProvisioningJobRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ProvisioningTimeoutScheduler {

    private final ProvisioningJobRepository jobRepository;
    private final ProvisioningSagaOrchestrator orchestrator;

    public ProvisioningTimeoutScheduler(ProvisioningJobRepository jobRepository, ProvisioningSagaOrchestrator orchestrator) {
        this.jobRepository = jobRepository;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${gentnt.provisioning.timeout-scheduler-interval-ms:60000}")
    public void handleExpiredJobs() {
        for (ProvisioningJob job : jobRepository.findByStatusAndExpiresAtBefore(ProvisioningJobStatus.IN_PROGRESS, Instant.now())) {
            orchestrator.handleTimeout(job.getId());
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew.bat :gen-tnt-starter:test --tests "com.example.tnt_svc.saga.RetryRecoverySchedulerTest" --tests "com.example.tnt_svc.saga.ProvisioningTimeoutSchedulerTest"`
Expected: PASS (2 tests)

- [ ] **Step 7: Run the full test suite**

Run: `./gradlew.bat :gen-tnt-starter:test`
Expected: all tests PASS across every task so far, no regressions.

- [ ] **Step 8: Commit**

```bash
git add gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/RetryRecoveryScheduler.java gen-tnt-starter/src/main/java/com/example/tnt_svc/saga/ProvisioningTimeoutScheduler.java gen-tnt-starter/src/test
git commit -m "feat: add retry-recovery and timeout-compensate schedulers"
```

---

### Task 10: End-to-end manual smoke script

**Files:**

- Create: `gen-tnt-demo/scripts/mock_step_server.py`
- Create: `gen-tnt-demo/scripts/smoke-provisioning.sh`
- Modify: `gen-tnt-demo/src/main/resources/application.yaml` (fill in a real 2-step pipeline pointing at the mock server)
- Modify: `README.md` (create if absent — document how to run the demo + smoke script)

**Interfaces:** none — standalone scripts, run manually against a real running demo app + Postgres + Redis + the mock step server.

- [ ] **Step 1: Write the mock step server**

```python
# gen-tnt-demo/scripts/mock_step_server.py
"""Tiny stdlib-only HTTP stub standing in for real provisioning step targets.
Auto-completes every sync step call with 200, and every async step call with
202 followed by a background callback POST back to Gen_TNT after a short delay."""
import json
import threading
import time
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

GEN_TNT_BASE = "http://localhost:8201"


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) or b"{}")

        if self.path == "/sync-step":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"schemaReady": True}).encode())
            return

        if self.path == "/async-step":
            self.send_response(202)
            self.end_headers()
            job_id = body.get("jobId")
            token = body.get("callbackToken")
            threading.Thread(target=self._fire_callback, args=(job_id, token), daemon=True).start()
            return

        self.send_response(404)
        self.end_headers()

    def _fire_callback(self, job_id, token):
        time.sleep(1)
        payload = json.dumps({"token": token, "success": True, "context": {"adminUserId": "mock-admin-1"}}).encode()
        req = urllib.request.Request(
            f"{GEN_TNT_BASE}/internal/provisioning/jobs/{job_id}/steps/AUTH_BOOTSTRAP/callback",
            data=payload,
            headers={"Content-Type": "application/json", "X-Internal-Secret": "dev-secret"},
            method="POST",
        )
        urllib.request.urlopen(req)

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("localhost", 9001), Handler).serve_forever()
```

- [ ] **Step 2: Write the smoke script**

```bash
#!/usr/bin/env bash
# gen-tnt-demo/scripts/smoke-provisioning.sh
# Manual smoke check. Requires: docker compose up -d (from repo root), the
# gen-tnt-demo app running (./gradlew.bat :gen-tnt-demo:bootRun), and this
# script's mock step server running (python3 scripts/mock_step_server.py).
set -euo pipefail

API_BASE="http://localhost:8201"
SECRET="dev-secret"
SLUG="smoke-$(date +%s)"

echo "1. create tenant"
CREATE_RESPONSE=$(curl -s -X POST "$API_BASE/api/v1/tenants" \
  -H "X-Internal-Secret: $SECRET" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"Smoke Test\",\"slug\":\"$SLUG\",\"region\":\"us\",\"primaryOwnerUserId\":\"$(uuidgen)\"}")
echo "   $CREATE_RESPONSE"

JOB_ID=$(echo "$CREATE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['provisioningJobId'])")
TENANT_ID=$(echo "$CREATE_RESPONSE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")

echo "2. poll job status until ACTIVE or DEAD"
for i in $(seq 1 20); do
  TENANT=$(curl -s "$API_BASE/api/v1/tenants/$TENANT_ID" -H "X-Internal-Secret: $SECRET")
  STATUS=$(echo "$TENANT" | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
  echo "   attempt $i: tenant status=$STATUS"
  if [ "$STATUS" = "ACTIVE" ]; then
    echo "SMOKE OK — tenant reached ACTIVE"
    exit 0
  fi
  sleep 1
done

echo "SMOKE FAILED — tenant never reached ACTIVE, last status=$STATUS"
exit 1
```

- [ ] **Step 3: Configure the demo app's step pipeline**

Modify `gen-tnt-demo/src/main/resources/application.yaml`'s `gentnt.provisioning.steps` list (replace the empty `steps: []`):

```yaml
gentnt:
  internal-secret: ${INTERNAL_SERVICE_SECRET:dev-secret}
  provisioning:
    max-retries: 3
    timeout-minutes: 10
    retry-scheduler-interval-ms: 120000
    timeout-scheduler-interval-ms: 60000
    steps:
      - name: SCHEMA_BOOTSTRAP
        url: http://localhost:9001/sync-step
        mode: SYNC
        retryable: true
      - name: AUTH_BOOTSTRAP
        url: http://localhost:9001/async-step
        mode: ASYNC
        retryable: true
```

- [ ] **Step 4: Run it against the real stack**

```bash
docker compose up -d
python3 gen-tnt-demo/scripts/mock_step_server.py &
INTERNAL_SERVICE_SECRET=dev-secret TNT_DB_URL=jdbc:postgresql://localhost:5435/gentnt TNT_DB_USERNAME=postgres TNT_DB_PASSWORD=postgres TNT_REDIS_HOST=localhost TNT_REDIS_PORT=6381 ./gradlew.bat :gen-tnt-demo:bootRun &
sleep 15
chmod +x gen-tnt-demo/scripts/smoke-provisioning.sh
./gen-tnt-demo/scripts/smoke-provisioning.sh
```

Expected: prints steps 1-2 and `SMOKE OK — tenant reached ACTIVE`, exit code 0.

- [ ] **Step 5: Write README.md**

```markdown
# Gen_TNT

Generalized tenant-provisioning service, genericized from CPMS-Platform's `tnt-svc`. Two modules:

- `gen-tnt-starter` — the reusable library (Tenant CRUD + provisioning saga), embeddable in any Spring Boot project.
- `gen-tnt-demo` — thin reference app deploying the starter standalone, for non-Java consumers to call over HTTP.

See `docs/superpowers/specs/2026-07-20-gen-tnt-provisioning-design.md` for the full design.

## Running it locally

```bash
docker compose up -d   # Postgres on 5435, Redis on 6381
python3 gen-tnt-demo/scripts/mock_step_server.py &
INTERNAL_SERVICE_SECRET=dev-secret \
TNT_DB_URL=jdbc:postgresql://localhost:5435/gentnt \
TNT_DB_USERNAME=postgres TNT_DB_PASSWORD=postgres \
TNT_REDIS_HOST=localhost TNT_REDIS_PORT=6381 \
./gradlew.bat :gen-tnt-demo:bootRun
```

Then in another terminal: `./gen-tnt-demo/scripts/smoke-provisioning.sh`
```

- [ ] **Step 6: Commit**

```bash
git add gen-tnt-demo/scripts gen-tnt-demo/src/main/resources/application.yaml README.md
git commit -m "test: add end-to-end manual smoke script for provisioning"
```

---

## Explicitly out of scope (carried over from the design doc)

- Gen_TBR (branding/custom-domain) — separate service, separate plan.
- Quotas, feature flags, region residency, billing snapshots, export/audit-hash-chain, support access, gRPC, RabbitMQ — none of this plan touches them.
- Parallel-fan-out step groups — v1 pipeline is strictly sequential.
- Publishing `gen-tnt-starter` to GitHub Packages for real (the `maven-publish` config exists, but no CI workflow runs it in this plan — add when a real consumer needs the published artifact, not speculatively).
- A Java consumer actually embedding `gen-tnt-starter` in-process — the module split exists, but nothing is wired up to do this yet.
