# Gen_ADM Phase 1 RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Gen_ADM as a Spring Boot autoconfiguration starter (`gen-adm-starter`) + reference demo app (`gen-adm-demo`) providing tenant-scoped RBAC: roles, a global permission-code catalog, user-role assignments, Postgres RLS for tenant isolation, and programmatic permission checking.

**Architecture:** Gradle multi-module project (`gen-adm-starter` library + `gen-adm-demo` reference app), matching Gen_AUTH's exact scaffold conventions. `GenAdmAutoConfiguration` component-scans `com.example.admsvc` so any host app gets the starter's beans regardless of its own base package. Tenant isolation is enforced at the Postgres layer via RLS, with a Spring AOP aspect setting the `app.tenant_id` session GUC per transaction — either from the resolved `GenAdmPrincipal` (normal request path) or from an explicit `@TenantIdParam`-annotated argument (the in-process tenant-bootstrap path, which has no HTTP request/principal at all).

**Tech Stack:** Java 21, Spring Boot 4.0.6, Gradle 9.4.1, PostgreSQL, Flyway, Spring Data JPA, Spring AOP, Lombok, JUnit 5, Mockito, Testcontainers.

## Global Constraints

- Group/package root: `com.example.admsvc` (matches source `adm-svc` package, keeps porting recognizable).
- Java toolchain: 21. Spring Boot BOM: 4.0.6. Gradle wrapper: 9.4.1 (copy verbatim from Gen_AUTH — same distribution).
- `gen-adm-starter` is a `java-library`, never applies the `org.springframework.boot` plugin itself (only `gen-adm-demo`, the runnable app, does) — same split as `gen-auth-starter`/`gen-auth-demo`.
- No owned user/client profile data anywhere — every table/DTO references external `tenantId`/`userId` UUIDs only, never email/name/status columns (per the approved spec's Scope section).
- No built-in default roles or permissions — the consumer supplies its own permission catalog via config; Gen_ADM never hardcodes role/permission data in a migration.
- Tenant isolation is Postgres RLS + an AOP aspect, not app-level `tenant_id` filtering alone (per the approved spec — this is new ground for Gen_MS, no sibling precedent, chosen deliberately).
- Fail loud, not fail-silent-empty: a missing tenant context throws `GenAdmConfigException`, never lets RLS silently return zero rows for a misconfigured caller.
- Cross-tenant lookups surface as 404 (`GenAdmNotFoundException`), never 403 — callers must not be able to distinguish "wrong tenant" from "never existed."
- Permission checking is programmatic only (`PermissionChecker.require(...)`) — no `@PreAuthorize` SpEL, no custom annotations for this plan.
- One final whole-branch review at the end of all tasks, not one per task (per your delivery-approach decision) — task-level reviews stay lightweight and task-scoped.

---

## Task 1: Gradle scaffold + autoconfiguration skeleton

**Files:**
- Create: `settings.gradle`
- Create: `gen-adm-starter/build.gradle`
- Create: `gen-adm-demo/build.gradle`
- Copy: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat` (from Gen_AUTH, unmodified — same Gradle distribution)
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/GenAdmStarterMarker.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/config/GenAdmAutoConfiguration.java`
- Create: `gen-adm-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Create: `gen-adm-demo/src/main/java/com/example/gendemo/GenAdmDemoApplication.java`
- Create: `gen-adm-demo/src/main/resources/application.yaml`
- Test: `gen-adm-demo/src/test/java/com/example/gendemo/GenAdmDemoApplicationTests.java`

**Interfaces:**
- Produces: `GenAdmStarterMarker` (public class, no-arg `version()` method returning `"0.1.0"`) — a trivial bean proving component-scan from a foreign package works. Later tasks do not depend on this class; it exists only for this task's own test.
- Produces: `GenAdmAutoConfiguration` — later tasks (2, 3) add `@EntityScan`/`@EnableJpaRepositories` to this same class; do not rename it.

This task deliberately does **not** add `spring-boot-starter-data-jpa`, `postgresql`, or `flyway-core` to `gen-adm-starter/build.gradle` yet — that happens in Task 2, together with the entities that need them. Keeping JPA off the classpath here means `gen-adm-demo`'s context loads with zero datasource configuration, so this task's test needs no database at all.

- [ ] **Step 1: Create `settings.gradle`**

```groovy
rootProject.name = 'gen-adm'
include(':gen-adm-starter', ':gen-adm-demo')
```

- [ ] **Step 2: Create `gen-adm-starter/build.gradle`**

```groovy
plugins {
    id 'java-library'
    id 'io.spring.dependency-management' version '1.1.7'
    id 'maven-publish'
}

description = 'gen-adm-starter'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

ext {
    testcontainersVersion = '1.21.3'
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
    // Web
    implementation 'org.springframework.boot:spring-boot-starter-web'

    // Security — GenAdmPrincipal integrates with Spring Security's
    // SecurityContext (Task 3); no JWT/JWKS code lives here, that stays
    // in Gen_AUTH.
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // Validation
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // AOP — backs the tenant-context aspect (Task 3)
    implementation 'org.springframework:spring-aop'
    implementation 'org.aspectj:aspectjweaver'

    // Observability
    implementation 'org.springframework.boot:spring-boot-starter-actuator'

    // Swagger / OpenAPI
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3'

    // Lombok
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Configuration Processor
    annotationProcessor 'org.springframework.boot:spring-boot-configuration-processor'

    // Testing
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testCompileOnly 'org.projectlombok:lombok'
    testAnnotationProcessor 'org.projectlombok:lombok'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}

publishing {
    publications {
        maven(MavenPublication) {
            groupId = 'com.example'
            artifactId = 'gen-adm-starter'
            version = project.version
            from components.java
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/GEN-MS/Gen_ADM")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

- [ ] **Step 3: Create `gen-adm-demo/build.gradle`**

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.0.6'
    id 'io.spring.dependency-management' version '1.1.7'
}

description = 'gen-adm-demo'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation project(':gen-adm-starter')
    // gen-adm-starter declares its Spring Boot deps as `implementation`,
    // which java-library does not expose transitively to a consumer's
    // compile classpath — this module needs its own spring-boot-starter
    // for @SpringBootApplication to resolve.
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

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Copy the Gradle wrapper from Gen_AUTH**

Run (from the Gen_ADM repo root):
```bash
cp -r "../Gen_AUTH/gradle" .
cp "../Gen_AUTH/gradlew" .
cp "../Gen_AUTH/gradlew.bat" .
```
Expected: `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat` now exist at the Gen_ADM repo root.

- [ ] **Step 5: Create the starter marker bean**

`gen-adm-starter/src/main/java/com/example/admsvc/GenAdmStarterMarker.java`:
```java
package com.example.admsvc;

import org.springframework.stereotype.Component;

/**
 * Trivial bean proving {@link com.example.admsvc.config.GenAdmAutoConfiguration}'s
 * component scan reaches this package from a host app in a different package tree.
 */
@Component
public class GenAdmStarterMarker {

    public String version() {
        return "0.1.0";
    }
}
```

- [ ] **Step 6: Create the autoconfiguration class**

`gen-adm-starter/src/main/java/com/example/admsvc/config/GenAdmAutoConfiguration.java`:
```java
package com.example.admsvc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * Makes every {@code @Service}/{@code @RestController}/{@code @Repository}/
 * {@code @ConfigurationProperties} class in the {@code com.example.admsvc}
 * package tree visible to a host application, regardless of that
 * application's own base package. Discovered automatically via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>Tasks 2/3 add {@code @EntityScan}/{@code @EnableJpaRepositories} here
 * once entities and repositories exist — do not rename this class.
 */
@AutoConfiguration
@ComponentScan("com.example.admsvc")
@ConfigurationPropertiesScan("com.example.admsvc")
public class GenAdmAutoConfiguration {
}
```

- [ ] **Step 7: Register the autoconfiguration**

`gen-adm-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.example.admsvc.config.GenAdmAutoConfiguration
```

- [ ] **Step 8: Create the demo application class**

`gen-adm-demo/src/main/java/com/example/gendemo/GenAdmDemoApplication.java`:
```java
package com.example.gendemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference application proving gen-adm-starter auto-configures correctly
 * from outside its own package — this class lives in
 * {@code com.example.gendemo}, not {@code com.example.admsvc}, specifically
 * so the starter's beans can only be found via
 * {@link com.example.admsvc.config.GenAdmAutoConfiguration}, not by
 * accidental component-scan overlap.
 */
@SpringBootApplication
public class GenAdmDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(GenAdmDemoApplication.class, args);
    }
}
```

- [ ] **Step 9: Create the demo application config**

`gen-adm-demo/src/main/resources/application.yaml`:
```yaml
server:
  port: 8106

spring:
  application:
    name: gen-adm-demo

management:
  endpoints:
    web:
      exposure:
        include:
          - health
          - info
```

- [ ] **Step 10: Write the context-load test**

`gen-adm-demo/src/test/java/com/example/gendemo/GenAdmDemoApplicationTests.java`:
```java
package com.example.gendemo;

import com.example.admsvc.GenAdmStarterMarker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class GenAdmDemoApplicationTests {

    @Autowired
    private GenAdmStarterMarker marker;

    @Test
    void contextLoadsAndStarterBeanIsPresent() {
        assertThat(marker.version()).isEqualTo("0.1.0");
    }
}
```

- [ ] **Step 11: Run the build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` — `gen-adm-starter` compiles, `gen-adm-demo`'s context loads and `GenAdmDemoApplicationTests` passes (1 test, 0 failures).

- [ ] **Step 12: Commit**

```bash
git add settings.gradle gen-adm-starter gen-adm-demo gradle gradlew gradlew.bat
git commit -m "feat: scaffold gen-adm-starter/gen-adm-demo Gradle modules with autoconfiguration skeleton"
```

---

## Task 2: RBAC persistence — entities, repositories, migration

**Files:**
- Modify: `gen-adm-starter/build.gradle` (add JPA/Postgres/Flyway/Testcontainers deps)
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/RoleEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/PermissionEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/UserRoleAssignmentEntity.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/RoleRepository.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/PermissionRepository.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/UserRoleAssignmentRepository.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/config/FlywayConfig.java`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V1__create_rbac_tables.sql`
- Modify: `gen-adm-starter/src/main/java/com/example/admsvc/config/GenAdmAutoConfiguration.java` (add `@EntityScan`/`@EnableJpaRepositories`)
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/RbacPersistenceIntegrationTest.java`

**Interfaces:**
- Consumes: nothing from Task 1 beyond the existing `GenAdmAutoConfiguration` class (this task edits it in place).
- Produces: `RoleEntity` (`UUID id`, `UUID tenantId`, `String name`, `String description`, `boolean systemRole`, `Long version`, `Instant createdAt`, `Instant updatedAt`, `Set<PermissionEntity> permissions`), `PermissionEntity` (`UUID id`, `String code`, `String description`, `Instant createdAt`), `UserRoleAssignmentEntity` (`UUID id`, `UUID tenantId`, `UUID userId`, `UUID roleId`, `Instant assignedAt`) — Task 3's RLS migration and Task 4's services depend on these exact field names and the `roles`/`permissions`/`role_permissions`/`user_role_assignments` table names.
- Produces: `RoleRepository.findByTenantIdAndName(UUID, String): Optional<RoleEntity>`, `RoleRepository.findAllByTenantId(UUID): List<RoleEntity>`, `RoleRepository.existsByTenantId(UUID): boolean`, `PermissionRepository.findByCode(String): Optional<PermissionEntity>`, `UserRoleAssignmentRepository.findAllByTenantIdAndUserId(UUID, UUID): List<UserRoleAssignmentEntity>`, `UserRoleAssignmentRepository.findByTenantIdAndUserIdAndRoleId(UUID, UUID, UUID): Optional<UserRoleAssignmentEntity>` — Task 4's services call these exact method signatures.

- [ ] **Step 1: Add persistence dependencies to `gen-adm-starter/build.gradle`**

In the `ext { ... }` block, no change needed. In the `dependencies { ... }` block, add (right after the AOP block, before Observability):
```groovy
    // Persistence
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    runtimeOnly 'org.postgresql:postgresql'

    // Flyway — plain library, NOT spring-boot-starter-flyway. gen-adm-starter
    // runs its own independent Flyway instance (see FlywayConfig below) so it
    // never collides with a host app's own Flyway migrations at the default
    // classpath:db/migration location.
    implementation 'org.flywaydb:flyway-core'
    runtimeOnly 'org.flywaydb:flyway-database-postgresql'
```
And in the testing block, add:
```groovy
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:postgresql'
```

- [ ] **Step 2: Write the failing integration test first**

`gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/persistence/RbacPersistenceIntegrationTest.java`:
```java
package com.example.admsvc.infrastructure.persistence;

import com.example.admsvc.config.GenAdmAutoConfiguration;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = RbacPersistenceIntegrationTest.TestApp.class)
class RbacPersistenceIntegrationTest {

    @SpringBootApplication
    static class TestApp {
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRoleAssignmentRepository assignmentRepository;

    @Test
    void savesAndFindsARoleByTenantAndName() {
        UUID tenantId = UUID.randomUUID();
        RoleEntity role = RoleEntity.builder()
                .tenantId(tenantId)
                .name("owner")
                .description("Tenant owner")
                .systemRole(false)
                .build();
        roleRepository.saveAndFlush(role);

        Optional<RoleEntity> found = roleRepository.findByTenantIdAndName(tenantId, "owner");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(role.getId());
    }

    @Test
    void rejectsDuplicateRoleNameWithinATenant() {
        UUID tenantId = UUID.randomUUID();
        roleRepository.saveAndFlush(RoleEntity.builder().tenantId(tenantId).name("owner").build());

        assertThatThrownBy(() ->
                roleRepository.saveAndFlush(RoleEntity.builder().tenantId(tenantId).name("owner").build())
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesAndFindsAPermissionByCode() {
        PermissionEntity permission = PermissionEntity.builder()
                .code("users:read")
                .description("Read users")
                .build();
        permissionRepository.saveAndFlush(permission);

        Optional<PermissionEntity> found = permissionRepository.findByCode("users:read");
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(permission.getId());
    }

    @Test
    void grantsAPermissionToARoleViaTheJoinTable() {
        UUID tenantId = UUID.randomUUID();
        PermissionEntity permission = permissionRepository.saveAndFlush(
                PermissionEntity.builder().code("billing:manage").build());
        RoleEntity role = RoleEntity.builder().tenantId(tenantId).name("billing-admin").build();
        role.getPermissions().add(permission);
        roleRepository.saveAndFlush(role);

        RoleEntity reloaded = roleRepository.findById(role.getId()).orElseThrow();
        assertThat(reloaded.getPermissions()).extracting(PermissionEntity::getCode)
                .containsExactly("billing:manage");
    }

    @Test
    void assignsARoleToAUserAndListsItByTenantAndUser() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RoleEntity role = roleRepository.saveAndFlush(
                RoleEntity.builder().tenantId(tenantId).name("member").build());

        UserRoleAssignmentEntity assignment = UserRoleAssignmentEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(role.getId())
                .build();
        assignmentRepository.saveAndFlush(assignment);

        List<UserRoleAssignmentEntity> found = assignmentRepository.findAllByTenantIdAndUserId(tenantId, userId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getRoleId()).isEqualTo(role.getId());
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.RbacPersistenceIntegrationTest"`
Expected: FAIL to compile — `RoleEntity`, `PermissionEntity`, `UserRoleAssignmentEntity`, `RoleRepository`, `PermissionRepository`, `UserRoleAssignmentRepository` do not exist yet.

- [ ] **Step 4: Create `RoleEntity`**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/RoleEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "name" }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_system_role", nullable = false)
    @Builder.Default
    private boolean systemRole = false;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))
    @Builder.Default
    private Set<PermissionEntity> permissions = new HashSet<>();

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
```

- [ ] **Step 5: Create `PermissionEntity`**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/PermissionEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 150)
    private String code;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }
}
```

- [ ] **Step 6: Create `UserRoleAssignmentEntity`**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/entity/UserRoleAssignmentEntity.java`:
```java
package com.example.admsvc.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_role_assignments", uniqueConstraints = @UniqueConstraint(columnNames = { "tenant_id", "user_id", "role_id" }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    void prePersist() {
        assignedAt = Instant.now();
    }
}
```

- [ ] **Step 7: Create the repositories**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/RoleRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<RoleEntity, UUID> {

    Optional<RoleEntity> findByTenantIdAndName(UUID tenantId, String name);

    List<RoleEntity> findAllByTenantId(UUID tenantId);

    boolean existsByTenantId(UUID tenantId);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/PermissionRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<PermissionEntity, UUID> {

    Optional<PermissionEntity> findByCode(String code);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/persistence/repository/UserRoleAssignmentRepository.java`:
```java
package com.example.admsvc.infrastructure.persistence.repository;

import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, UUID> {

    List<UserRoleAssignmentEntity> findAllByTenantIdAndUserId(UUID tenantId, UUID userId);

    Optional<UserRoleAssignmentEntity> findByTenantIdAndUserIdAndRoleId(UUID tenantId, UUID userId, UUID roleId);

    void deleteByTenantIdAndUserIdAndRoleId(UUID tenantId, UUID userId, UUID roleId);
}
```

- [ ] **Step 8: Create the migration**

`gen-adm-starter/src/main/resources/db/migration/genadm/V1__create_rbac_tables.sql`:
```sql
CREATE TABLE roles (
    id             UUID PRIMARY KEY,
    tenant_id      UUID NOT NULL,
    name           VARCHAR(100) NOT NULL,
    description    VARCHAR(500),
    is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
    version        BIGINT,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_roles_tenant_name UNIQUE (tenant_id, name)
);

CREATE TABLE permissions (
    id          UUID PRIMARY KEY,
    code        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_role_assignments (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    role_id     UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_assignments_tenant_user_role UNIQUE (tenant_id, user_id, role_id)
);

CREATE INDEX idx_user_role_assignments_tenant_user ON user_role_assignments(tenant_id, user_id);
```

- [ ] **Step 9: Create the Flyway config**

`gen-adm-starter/src/main/java/com/example/admsvc/config/FlywayConfig.java`:
```java
package com.example.admsvc.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;

/**
 * Runs gen-adm-starter's own Flyway migrations from
 * {@code classpath:db/migration/genadm}, independent of the host app's own
 * Flyway setup (if any) at the default {@code classpath:db/migration}
 * location — so the two never collide or double-apply each other's scripts.
 */
@Configuration
@AutoConfigureAfter({ DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class })
public class FlywayConfig {

    @Bean
    public Flyway genAdmFlyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/genadm")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        return flyway;
    }

    /**
     * Guarantees gen-adm's migration runs before Hibernate validates the
     * schema, even though this bean has no direct dependency edge to the
     * EntityManagerFactory.
     */
    @EventListener(ContextRefreshedEvent.class)
    public void noop() {
        // Bean creation order above is sufficient; this listener exists only
        // to document the ordering requirement for future readers.
    }
}
```

- [ ] **Step 10: Add entity/repository scanning to the autoconfiguration**

Edit `gen-adm-starter/src/main/java/com/example/admsvc/config/GenAdmAutoConfiguration.java` — replace its full contents:
```java
package com.example.admsvc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Makes every {@code @Service}/{@code @RestController}/{@code @Repository}/
 * {@code @ConfigurationProperties} class in the {@code com.example.admsvc}
 * package tree visible to a host application, regardless of that
 * application's own base package. Discovered automatically via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 */
@AutoConfiguration
@ComponentScan("com.example.admsvc")
@EntityScan("com.example.admsvc.infrastructure.persistence.entity")
@EnableJpaRepositories("com.example.admsvc.infrastructure.persistence.repository")
@ConfigurationPropertiesScan("com.example.admsvc")
public class GenAdmAutoConfiguration {
}
```

- [ ] **Step 11: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.persistence.RbacPersistenceIntegrationTest"`
Expected: PASS (5 tests, 0 failures). Requires Docker running locally for Testcontainers.

- [ ] **Step 12: Run the full build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` — Task 1's demo test still passes (JPA additions don't affect `gen-adm-demo`, which still has no datasource configured).

- [ ] **Step 13: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add RBAC entities, repositories, and Flyway migration"
```

---

## Task 3: Postgres RLS + tenant-context aspect

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmException.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmConfigException.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/port/GenAdmPrincipal.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/domain/port/TenantIdParam.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/security/TenantContextAspect.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/config/SecurityConfig.java`
- Create: `gen-adm-starter/src/main/resources/db/migration/genadm/V2__enable_rls.sql`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/security/TenantIsolationIntegrationTest.java`

**Interfaces:**
- Consumes: `RoleEntity`, `UserRoleAssignmentEntity`, `RoleRepository`, `UserRoleAssignmentRepository` from Task 2.
- Produces: `GenAdmException` (abstract base, fields `String code`, inherited `getMessage()`), `GenAdmConfigException extends GenAdmException` (code `"CONFIG_ERROR"`) — Task 4 adds sibling subclasses (`GenAdmNotFoundException`, `GenAdmConflictException`, `GenAdmValidationException`, `GenAdmForbiddenException`) extending the same base.
- Produces: `GenAdmPrincipal` interface (`UUID tenantId()`, `UUID userId()`) — Task 4's `PermissionChecker` and Task 6's controllers depend on this exact interface.
- Produces: `@TenantIdParam` (parameter annotation) — Task 4's `bootstrapTenant` method annotates its `tenantId` parameter with this so the aspect uses it directly instead of looking for a `GenAdmPrincipal` in `SecurityContextHolder`.
- Produces: `TenantContextAspect` — an `@Aspect` intercepting every `@Transactional` method in `com.example.admsvc.application.impl`. Task 4's service implementations must live in that exact package and mark their methods `@Transactional` for this aspect to apply.

- [ ] **Step 1: Create the exception base classes**

`gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmException.java`:
```java
package com.example.admsvc.common.exception;

public abstract class GenAdmException extends RuntimeException {

    private final String code;

    protected GenAdmException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmConfigException.java`:
```java
package com.example.admsvc.common.exception;

/**
 * Thrown when a Gen_ADM call has no resolvable tenant context — e.g. no
 * {@link com.example.admsvc.domain.port.GenAdmPrincipal} in
 * {@code SecurityContextHolder} and no {@code @TenantIdParam}-annotated
 * argument. Fails loud rather than letting RLS silently return zero rows
 * for what would look like a "not found" instead of a misconfigured caller.
 */
public class GenAdmConfigException extends GenAdmException {

    public GenAdmConfigException(String message) {
        super("CONFIG_ERROR", message);
    }
}
```

- [ ] **Step 2: Create the `GenAdmPrincipal` port**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/port/GenAdmPrincipal.java`:
```java
package com.example.admsvc.domain.port;

import java.util.UUID;

/**
 * Implemented by whatever object the host application's
 * {@code Authentication#getPrincipal()} returns (directly, or via a small
 * adapter the host registers) — Gen_ADM never imports a Gen_AUTH class
 * directly, staying loosely coupled to any specific auth library.
 */
public interface GenAdmPrincipal {

    UUID tenantId();

    UUID userId();
}
```

- [ ] **Step 3: Create the `@TenantIdParam` annotation**

`gen-adm-starter/src/main/java/com/example/admsvc/domain/port/TenantIdParam.java`:
```java
package com.example.admsvc.domain.port;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code UUID} method parameter as the tenant ID
 * {@link com.example.admsvc.infrastructure.security.TenantContextAspect}
 * should use directly, instead of resolving one from a
 * {@link GenAdmPrincipal} in {@code SecurityContextHolder}. Used by the
 * in-process tenant-bootstrap path, which runs with no HTTP request or
 * authenticated principal at all.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantIdParam {
}
```

- [ ] **Step 4: Write the failing RLS integration test first**

`gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/security/TenantIsolationIntegrationTest.java`:
```java
package com.example.admsvc.infrastructure.security;

import com.example.admsvc.common.exception.GenAdmConfigException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(classes = TenantIsolationIntegrationTest.TestApp.class)
class TenantIsolationIntegrationTest {

    @SpringBootApplication
    @ContextConfiguration
    static class TestApp {
    }

    /** Minimal service under test, living in application.impl so the aspect's pointcut applies. */
    @Service
    static class ProbeService {

        private final RoleRepository roleRepository;

        ProbeService(RoleRepository roleRepository) {
            this.roleRepository = roleRepository;
        }

        @Transactional
        public List<RoleEntity> listRolesForCurrentTenant() {
            // In real services this would be tenant-scoped via the caller's
            // own tenantId; here we deliberately query ALL roles to prove
            // RLS — not application code — is what filters them.
            return roleRepository.findAll();
        }

        @Transactional
        public RoleEntity createRoleViaBootstrap(@TenantIdParam UUID tenantId, String name) {
            RoleEntity role = RoleEntity.builder().tenantId(tenantId).name(name).build();
            return roleRepository.saveAndFlush(role);
        }
    }

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProbeService probeService;

    @Autowired
    private EntityManager entityManager;

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private void authenticateAs(UUID tenantId, UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(new TestPrincipal(tenantId, userId), null));
    }

    @Test
    void aTenantCannotSeeAnotherTenantsRoles() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();

        authenticateAs(tenantA, UUID.randomUUID());
        probeService.createRoleViaBootstrap(tenantA, "owner");

        authenticateAs(tenantB, UUID.randomUUID());
        probeService.createRoleViaBootstrap(tenantB, "owner");

        authenticateAs(tenantA, UUID.randomUUID());
        List<RoleEntity> visibleToA = probeService.listRolesForCurrentTenant();

        assertThat(visibleToA).hasSize(1);
        assertThat(visibleToA.get(0).getTenantId()).isEqualTo(tenantA);
    }

    @Test
    void missingPrincipalAndNoTenantIdParamThrowsConfigExceptionBeforeAnyQuery() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> probeService.listRolesForCurrentTenant())
                .isInstanceOf(GenAdmConfigException.class);
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.security.TenantIsolationIntegrationTest"`
Expected: FAIL — RLS is not enabled yet (both tests would see both tenants' roles, or compilation fails on `TenantContextAspect` not existing).

- [ ] **Step 6: Create the RLS migration**

`gen-adm-starter/src/main/resources/db/migration/genadm/V2__enable_rls.sql`:
```sql
ALTER TABLE roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE roles FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_roles ON roles
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE user_role_assignments ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_role_assignments FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_user_role_assignments ON user_role_assignments
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- permissions stays global (no tenant_id column) — not policed by RLS.
```

- [ ] **Step 7: Create the tenant-context aspect**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/security/TenantContextAspect.java`:
```java
package com.example.admsvc.infrastructure.security;

import com.example.admsvc.common.exception.GenAdmConfigException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.domain.port.TenantIdParam;
import jakarta.persistence.EntityManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Sets the {@code app.tenant_id} Postgres session GUC for the duration of
 * every {@code @Transactional} method in {@code com.example.admsvc.application.impl},
 * so the RLS policies from V2__enable_rls.sql actually filter queries —
 * this is the piece {@code tbr-svc} (a sibling CPMS service) never wired up,
 * leaving its own RLS silently dead.
 *
 * <p>Resolves the tenant ID two ways, in order: (1) a {@code @TenantIdParam}-
 * annotated {@code UUID} argument, if present — used by the in-process
 * tenant-bootstrap path, which has no HTTP request or authenticated
 * principal; (2) the {@link GenAdmPrincipal#tenantId()} of the current
 * {@code SecurityContextHolder} authentication, for every normal
 * request-driven call. If neither is available, throws
 * {@link GenAdmConfigException} before any query runs.
 */
@Aspect
@Component
public class TenantContextAspect {

    private final EntityManager entityManager;

    public TenantContextAspect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Around("execution(* com.example.admsvc.application.impl..*(..)) && @annotation(org.springframework.transaction.annotation.Transactional)")
    public Object setTenantContext(ProceedingJoinPoint joinPoint) throws Throwable {
        UUID tenantId = resolveTenantId(joinPoint);
        if (tenantId == null) {
            throw new GenAdmConfigException(
                    "No tenant context available for " + joinPoint.getSignature());
        }
        entityManager.createNativeQuery("SET LOCAL app.tenant_id = :tenantId")
                .setParameter("tenantId", tenantId.toString())
                .executeUpdate();
        return joinPoint.proceed();
    }

    private UUID resolveTenantId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameterAnnotations.length; i++) {
            for (Annotation annotation : parameterAnnotations[i]) {
                if (annotation instanceof TenantIdParam && args[i] instanceof UUID uuidArg) {
                    return uuidArg;
                }
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof GenAdmPrincipal principal) {
            return principal.tenantId();
        }
        return null;
    }
}
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.security.TenantIsolationIntegrationTest"`
Expected: PASS (2 tests, 0 failures).

- [ ] **Step 9: Add a permissive Spring Security config**

`spring-boot-starter-security` (added in Task 1) auto-configures a default
filter chain requiring HTTP Basic auth on every request unless a
`SecurityFilterChain` bean says otherwise. Gen_ADM does its own
authorization via `PermissionChecker` (Task 4), not Spring Security's — so
the starter must ship a `SecurityFilterChain` that permits all requests,
otherwise any host app (including `gen-adm-demo` in Task 7) gets a random
generated password and 401s on every route the moment
`spring-boot-starter-security` is on the classpath. This doesn't affect any
test up through this task (`TenantIsolationIntegrationTest` calls the
service bean directly, never through HTTP; Task 6's controller tests use
`MockMvcBuilders.standaloneSetup`, which never loads the real filter chain
either) — it only becomes observable once Task 7's demo app actually
receives real HTTP requests, which is why it's easy to miss without this
explicit step.

`gen-adm-starter/src/main/java/com/example/admsvc/config/SecurityConfig.java`:
```java
package com.example.admsvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Gen_ADM performs its own authorization via {@code PermissionChecker}
 * (populated from {@code GenAdmPrincipal} in {@code SecurityContextHolder},
 * which the host app is responsible for setting) — this permits every
 * request at the Spring Security layer so its default Basic Auth
 * auto-configuration never engages. Authorization enforcement is
 * exclusively {@code PermissionChecker.require(...)}'s job.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain genAdmSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
```

- [ ] **Step 10: Run the full starter test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: all tests pass (Task 2's persistence tests + Task 3's tenant-isolation tests).

- [ ] **Step 11: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: enable Postgres RLS, add tenant-context AOP aspect, permit all requests at the security-filter layer"
```

---

## Task 4: Application services — RoleService, UserRoleAssignmentService, PermissionChecker

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmNotFoundException.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmConflictException.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmValidationException.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmForbiddenException.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/RoleService.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/RoleServiceImpl.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/UserRoleAssignmentService.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImpl.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/service/PermissionChecker.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/application/impl/PermissionCheckerImpl.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/RoleServiceImplTest.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImplTest.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/application/impl/PermissionCheckerImplTest.java`

**Interfaces:**
- Consumes: `RoleRepository`, `PermissionRepository`, `UserRoleAssignmentRepository` (Task 2); `GenAdmPrincipal`, `TenantIdParam`, `GenAdmException` (Task 3).
- Produces: `RoleService.createRole(UUID tenantId, String name, String description): RoleEntity`, `.getRole(UUID tenantId, UUID roleId): RoleEntity`, `.listRoles(UUID tenantId): List<RoleEntity>`, `.grantPermissions(UUID tenantId, UUID roleId, Set<String> codes): RoleEntity`, `.revokePermissions(UUID tenantId, UUID roleId, Set<String> codes): RoleEntity`, `.deleteRole(UUID tenantId, UUID roleId): void` — Task 6's `RoleController` calls these exact signatures.
- Produces: `UserRoleAssignmentService.assignRole(UUID tenantId, UUID userId, UUID roleId): UserRoleAssignmentEntity`, `.revokeRole(UUID tenantId, UUID userId, UUID roleId): void`, `.listAssignments(UUID tenantId, UUID userId): List<UserRoleAssignmentEntity>`, `.effectivePermissionCodes(UUID tenantId, UUID userId): Set<String>`, `.bootstrapTenant(@TenantIdParam UUID tenantId, UUID userId, String roleName, Set<String> permissionCodes): UserRoleAssignmentEntity` — Task 6's `AssignmentController` and Task 7's demo wiring call these.
- Produces: `PermissionChecker.has(GenAdmPrincipal principal, String code): boolean`, `.require(GenAdmPrincipal principal, String code): void` (throws `GenAdmForbiddenException`) — Task 6's controllers call `require(...)` at the top of each mutating endpoint.

- [ ] **Step 1: Create the remaining exception classes**

`gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmNotFoundException.java`:
```java
package com.example.admsvc.common.exception;

public class GenAdmNotFoundException extends GenAdmException {

    public GenAdmNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmConflictException.java`:
```java
package com.example.admsvc.common.exception;

public class GenAdmConflictException extends GenAdmException {

    public GenAdmConflictException(String message) {
        super("CONFLICT", message);
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmValidationException.java`:
```java
package com.example.admsvc.common.exception;

public class GenAdmValidationException extends GenAdmException {

    public GenAdmValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/common/exception/GenAdmForbiddenException.java`:
```java
package com.example.admsvc.common.exception;

public class GenAdmForbiddenException extends GenAdmException {

    public GenAdmForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
```

- [ ] **Step 2: Write the failing `RoleService` test first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/RoleServiceImplTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private RoleServiceImpl roleService;

    private final UUID tenantId = UUID.randomUUID();

    @Test
    void createsARole() {
        when(roleRepository.findByTenantIdAndName(tenantId, "owner")).thenReturn(Optional.empty());
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleEntity created = roleService.createRole(tenantId, "owner", "Tenant owner");

        assertThat(created.getName()).isEqualTo("owner");
        assertThat(created.getTenantId()).isEqualTo(tenantId);
        verify(roleRepository).save(any(RoleEntity.class));
    }

    @Test
    void rejectsDuplicateRoleName() {
        when(roleRepository.findByTenantIdAndName(tenantId, "owner"))
                .thenReturn(Optional.of(RoleEntity.builder().tenantId(tenantId).name("owner").build()));

        assertThatThrownBy(() -> roleService.createRole(tenantId, "owner", "dup"))
                .isInstanceOf(GenAdmConflictException.class);
    }

    @Test
    void throwsNotFoundWhenRoleDoesNotExist() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getRole(tenantId, roleId))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void throwsNotFoundWhenRoleBelongsToAnotherTenant() {
        UUID roleId = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        when(roleRepository.findById(roleId))
                .thenReturn(Optional.of(RoleEntity.builder().id(roleId).tenantId(otherTenant).name("x").build()));

        assertThatThrownBy(() -> roleService.getRole(tenantId, roleId))
                .isInstanceOf(GenAdmNotFoundException.class);
    }

    @Test
    void grantsPermissionsToARole() {
        UUID roleId = UUID.randomUUID();
        RoleEntity role = RoleEntity.builder().id(roleId).tenantId(tenantId).name("billing-admin").build();
        PermissionEntity permission = PermissionEntity.builder().code("billing:manage").build();

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(permissionRepository.findByCode("billing:manage")).thenReturn(Optional.of(permission));
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleEntity updated = roleService.grantPermissions(tenantId, roleId, Set.of("billing:manage"));

        assertThat(updated.getPermissions()).extracting(PermissionEntity::getCode)
                .containsExactly("billing:manage");
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.RoleServiceImplTest"`
Expected: FAIL to compile — `RoleService`/`RoleServiceImpl` don't exist yet.

- [ ] **Step 4: Create `RoleService` and `RoleServiceImpl`**

`gen-adm-starter/src/main/java/com/example/admsvc/application/service/RoleService.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface RoleService {

    RoleEntity createRole(UUID tenantId, String name, String description);

    RoleEntity getRole(UUID tenantId, UUID roleId);

    List<RoleEntity> listRoles(UUID tenantId);

    RoleEntity grantPermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes);

    RoleEntity revokePermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes);

    void deleteRole(UUID tenantId, UUID roleId);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/RoleServiceImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.RoleService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleServiceImpl(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional
    public RoleEntity createRole(UUID tenantId, String name, String description) {
        roleRepository.findByTenantIdAndName(tenantId, name).ifPresent(existing -> {
            throw new GenAdmConflictException("Role '" + name + "' already exists for this tenant");
        });
        RoleEntity role = RoleEntity.builder()
                .tenantId(tenantId)
                .name(name)
                .description(description)
                .build();
        return roleRepository.save(role);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleEntity getRole(UUID tenantId, UUID roleId) {
        return findOwnedRole(tenantId, roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleEntity> listRoles(UUID tenantId) {
        return roleRepository.findAllByTenantId(tenantId);
    }

    @Override
    @Transactional
    public RoleEntity grantPermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes) {
        RoleEntity role = findOwnedRole(tenantId, roleId);
        for (String code : permissionCodes) {
            PermissionEntity permission = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new GenAdmValidationException("Unknown permission code: " + code));
            role.getPermissions().add(permission);
        }
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public RoleEntity revokePermissions(UUID tenantId, UUID roleId, Set<String> permissionCodes) {
        RoleEntity role = findOwnedRole(tenantId, roleId);
        role.getPermissions().removeIf(p -> permissionCodes.contains(p.getCode()));
        return roleRepository.save(role);
    }

    @Override
    @Transactional
    public void deleteRole(UUID tenantId, UUID roleId) {
        RoleEntity role = findOwnedRole(tenantId, roleId);
        roleRepository.delete(role);
    }

    private RoleEntity findOwnedRole(UUID tenantId, UUID roleId) {
        RoleEntity role = roleRepository.findById(roleId)
                .orElseThrow(() -> new GenAdmNotFoundException("Role not found: " + roleId));
        if (!role.getTenantId().equals(tenantId)) {
            // Cross-tenant lookup surfaces as 404, never 403 — RLS would
            // already prevent this in a real transaction; this check keeps
            // the service correct even when called with a repository mock
            // in tests, or bypassing the aspect entirely.
            throw new GenAdmNotFoundException("Role not found: " + roleId);
        }
        return role;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.RoleServiceImplTest"`
Expected: PASS (5 tests, 0 failures).

- [ ] **Step 6: Write the failing `UserRoleAssignmentService` test first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImplTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRoleAssignmentServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserRoleAssignmentRepository assignmentRepository;

    @InjectMocks
    private UserRoleAssignmentServiceImpl service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void assignsARoleToAUser() {
        UUID roleId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(UserRoleAssignmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserRoleAssignmentEntity assignment = service.assignRole(tenantId, userId, roleId);

        assertThat(assignment.getTenantId()).isEqualTo(tenantId);
        assertThat(assignment.getUserId()).isEqualTo(userId);
        assertThat(assignment.getRoleId()).isEqualTo(roleId);
    }

    @Test
    void rejectsAssigningTheSameRoleToTheSameUserTwice() {
        UUID roleId = UUID.randomUUID();
        when(assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId))
                .thenReturn(Optional.of(UserRoleAssignmentEntity.builder()
                        .tenantId(tenantId).userId(userId).roleId(roleId).build()));

        assertThatThrownBy(() -> service.assignRole(tenantId, userId, roleId))
                .isInstanceOf(GenAdmConflictException.class);

        verify(assignmentRepository, never()).save(any());
    }

    @Test
    void listsEffectivePermissionCodesAcrossAllAssignedRoles() {
        RoleEntity roleA = RoleEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("a").build();
        roleA.getPermissions().add(PermissionEntity.builder().code("users:read").build());
        RoleEntity roleB = RoleEntity.builder().id(UUID.randomUUID()).tenantId(tenantId).name("b").build();
        roleB.getPermissions().add(PermissionEntity.builder().code("billing:manage").build());

        when(assignmentRepository.findAllByTenantIdAndUserId(tenantId, userId)).thenReturn(List.of(
                UserRoleAssignmentEntity.builder().tenantId(tenantId).userId(userId).roleId(roleA.getId()).build(),
                UserRoleAssignmentEntity.builder().tenantId(tenantId).userId(userId).roleId(roleB.getId()).build()
        ));
        when(roleRepository.findById(roleA.getId())).thenReturn(Optional.of(roleA));
        when(roleRepository.findById(roleB.getId())).thenReturn(Optional.of(roleB));

        Set<String> codes = service.effectivePermissionCodes(tenantId, userId);

        assertThat(codes).containsExactlyInAnyOrder("users:read", "billing:manage");
    }

    @Test
    void bootstrapCreatesFirstRoleWhenTenantHasNone() {
        when(roleRepository.existsByTenantId(tenantId)).thenReturn(false);
        when(roleRepository.save(any(RoleEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(permissionRepository.findByCode("adm:roles:manage"))
                .thenReturn(Optional.of(PermissionEntity.builder().code("adm:roles:manage").build()));
        when(assignmentRepository.save(any(UserRoleAssignmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        UserRoleAssignmentEntity assignment = service.bootstrapTenant(
                tenantId, userId, "owner", Set.of("adm:roles:manage"));

        assertThat(assignment.getTenantId()).isEqualTo(tenantId);
        assertThat(assignment.getUserId()).isEqualTo(userId);
        verify(roleRepository).save(any(RoleEntity.class));
    }

    @Test
    void bootstrapRejectsATenantThatAlreadyHasRoles() {
        when(roleRepository.existsByTenantId(tenantId)).thenReturn(true);

        assertThatThrownBy(() -> service.bootstrapTenant(tenantId, userId, "owner", Set.of("adm:roles:manage")))
                .isInstanceOf(GenAdmConflictException.class);

        verify(roleRepository, never()).save(any());
    }
}
```

- [ ] **Step 7: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.UserRoleAssignmentServiceImplTest"`
Expected: FAIL to compile — `UserRoleAssignmentService`/`UserRoleAssignmentServiceImpl` don't exist yet.

- [ ] **Step 8: Create `UserRoleAssignmentService` and its implementation**

`gen-adm-starter/src/main/java/com/example/admsvc/application/service/UserRoleAssignmentService.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserRoleAssignmentService {

    UserRoleAssignmentEntity assignRole(UUID tenantId, UUID userId, UUID roleId);

    void revokeRole(UUID tenantId, UUID userId, UUID roleId);

    List<UserRoleAssignmentEntity> listAssignments(UUID tenantId, UUID userId);

    Set<String> effectivePermissionCodes(UUID tenantId, UUID userId);

    /**
     * Programmatic-only, permission-check-free path for a fresh tenant's
     * first role — no HTTP route exists for this. Callable from a host
     * app's own tenant-provisioning flow with no authenticated principal
     * in context, which is why {@code tenantId} is {@code @TenantIdParam}-
     * annotated: {@link com.example.admsvc.infrastructure.security.TenantContextAspect}
     * uses it directly instead of looking for a principal.
     * Throws {@link com.example.admsvc.common.exception.GenAdmConflictException}
     * if the tenant already has at least one role.
     */
    UserRoleAssignmentEntity bootstrapTenant(
            @TenantIdParam UUID tenantId, UUID userId, String roleName, Set<String> permissionCodes);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/UserRoleAssignmentServiceImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmConflictException;
import com.example.admsvc.common.exception.GenAdmValidationException;
import com.example.admsvc.domain.port.TenantIdParam;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import com.example.admsvc.infrastructure.persistence.repository.RoleRepository;
import com.example.admsvc.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class UserRoleAssignmentServiceImpl implements UserRoleAssignmentService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleAssignmentRepository assignmentRepository;

    public UserRoleAssignmentServiceImpl(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            UserRoleAssignmentRepository assignmentRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @Override
    @Transactional
    public UserRoleAssignmentEntity assignRole(UUID tenantId, UUID userId, UUID roleId) {
        assignmentRepository.findByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId).ifPresent(existing -> {
            throw new GenAdmConflictException("User already holds this role");
        });
        UserRoleAssignmentEntity assignment = UserRoleAssignmentEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(roleId)
                .build();
        return assignmentRepository.save(assignment);
    }

    @Override
    @Transactional
    public void revokeRole(UUID tenantId, UUID userId, UUID roleId) {
        assignmentRepository.deleteByTenantIdAndUserIdAndRoleId(tenantId, userId, roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRoleAssignmentEntity> listAssignments(UUID tenantId, UUID userId) {
        return assignmentRepository.findAllByTenantIdAndUserId(tenantId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> effectivePermissionCodes(UUID tenantId, UUID userId) {
        List<UserRoleAssignmentEntity> assignments = assignmentRepository.findAllByTenantIdAndUserId(tenantId, userId);
        Set<String> codes = new HashSet<>();
        for (UserRoleAssignmentEntity assignment : assignments) {
            roleRepository.findById(assignment.getRoleId()).ifPresent(role ->
                    role.getPermissions().forEach(p -> codes.add(p.getCode())));
        }
        return codes;
    }

    @Override
    @Transactional
    public UserRoleAssignmentEntity bootstrapTenant(
            @TenantIdParam UUID tenantId, UUID userId, String roleName, Set<String> permissionCodes) {
        if (roleRepository.existsByTenantId(tenantId)) {
            throw new GenAdmConflictException(
                    "Tenant " + tenantId + " already has roles — bootstrapTenant is for first-role creation only");
        }
        RoleEntity role = RoleEntity.builder()
                .tenantId(tenantId)
                .name(roleName)
                .build();
        for (String code : permissionCodes) {
            PermissionEntity permission = permissionRepository.findByCode(code)
                    .orElseThrow(() -> new GenAdmValidationException("Unknown permission code: " + code));
            role.getPermissions().add(permission);
        }
        RoleEntity savedRole = roleRepository.save(role);

        UserRoleAssignmentEntity assignment = UserRoleAssignmentEntity.builder()
                .tenantId(tenantId)
                .userId(userId)
                .roleId(savedRole.getId())
                .build();
        return assignmentRepository.save(assignment);
    }
}
```

- [ ] **Step 9: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.UserRoleAssignmentServiceImplTest"`
Expected: PASS (5 tests, 0 failures).

- [ ] **Step 10: Write the failing `PermissionChecker` test first**

`gen-adm-starter/src/test/java/com/example/admsvc/application/impl/PermissionCheckerImplTest.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionCheckerImplTest {

    @Mock
    private UserRoleAssignmentService assignmentService;

    @InjectMocks
    private PermissionCheckerImpl permissionChecker;

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @Test
    void hasReturnsTrueWhenPrincipalHoldsTheCode() {
        when(assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId()))
                .thenReturn(Set.of("users:read"));

        assertThat(permissionChecker.has(principal, "users:read")).isTrue();
    }

    @Test
    void hasReturnsFalseWhenPrincipalLacksTheCode() {
        when(assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId()))
                .thenReturn(Set.of());

        assertThat(permissionChecker.has(principal, "users:read")).isFalse();
    }

    @Test
    void requireThrowsForbiddenWhenPrincipalLacksTheCode() {
        when(assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId()))
                .thenReturn(Set.of());

        assertThatThrownBy(() -> permissionChecker.require(principal, "adm:roles:manage"))
                .isInstanceOf(GenAdmForbiddenException.class);
    }

    @Test
    void requireDoesNotThrowWhenPrincipalHoldsTheCode() {
        when(assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId()))
                .thenReturn(Set.of("adm:roles:manage"));

        permissionChecker.require(principal, "adm:roles:manage");
        // no exception = pass
    }
}
```

- [ ] **Step 11: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.PermissionCheckerImplTest"`
Expected: FAIL to compile — `PermissionChecker`/`PermissionCheckerImpl` don't exist yet.

- [ ] **Step 12: Create `PermissionChecker` and its implementation**

`gen-adm-starter/src/main/java/com/example/admsvc/application/service/PermissionChecker.java`:
```java
package com.example.admsvc.application.service;

import com.example.admsvc.domain.port.GenAdmPrincipal;

public interface PermissionChecker {

    boolean has(GenAdmPrincipal principal, String permissionCode);

    /**
     * Throws {@link com.example.admsvc.common.exception.GenAdmForbiddenException}
     * if {@code principal} does not hold {@code permissionCode}.
     */
    void require(GenAdmPrincipal principal, String permissionCode);
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/application/impl/PermissionCheckerImpl.java`:
```java
package com.example.admsvc.application.impl;

import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionCheckerImpl implements PermissionChecker {

    private final UserRoleAssignmentService assignmentService;

    public PermissionCheckerImpl(UserRoleAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean has(GenAdmPrincipal principal, String permissionCode) {
        return assignmentService.effectivePermissionCodes(principal.tenantId(), principal.userId())
                .contains(permissionCode);
    }

    @Override
    @Transactional(readOnly = true)
    public void require(GenAdmPrincipal principal, String permissionCode) {
        if (!has(principal, permissionCode)) {
            throw new GenAdmForbiddenException("Missing required permission: " + permissionCode);
        }
    }
}
```

- [ ] **Step 13: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.application.impl.PermissionCheckerImplTest"`
Expected: PASS (4 tests, 0 failures).

- [ ] **Step 14: Run the full starter test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: all tests pass (Tasks 2, 3, and 4's tests together).

- [ ] **Step 15: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add RoleService, UserRoleAssignmentService, and PermissionChecker"
```

---

## Task 5: Permission catalog configuration

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/config/properties/GenAdmProperties.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/startup/PermissionCatalogInitializer.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/startup/PermissionCatalogInitializerTest.java`

**Interfaces:**
- Consumes: `PermissionRepository` (Task 2).
- Produces: `GenAdmProperties` (`@ConfigurationProperties(prefix = "gen-adm")`, field `List<PermissionDefinition> permissions`, nested `record PermissionDefinition(String code, String description)`) — Task 7's demo app config supplies values under this prefix.

- [ ] **Step 1: Write the failing initializer test first**

`gen-adm-starter/src/test/java/com/example/admsvc/infrastructure/startup/PermissionCatalogInitializerTest.java`:
```java
package com.example.admsvc.infrastructure.startup;

import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionCatalogInitializerTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Test
    void createsPermissionsThatDoNotExistYet() {
        GenAdmProperties properties = new GenAdmProperties();
        properties.setPermissions(List.of(
                new GenAdmProperties.PermissionDefinition("users:read", "Read users"),
                new GenAdmProperties.PermissionDefinition("adm:roles:manage", "Manage roles")
        ));
        when(permissionRepository.findByCode(anyString())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        new PermissionCatalogInitializer(properties, permissionRepository).run(null);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PermissionEntity::getCode)
                .containsExactlyInAnyOrder("users:read", "adm:roles:manage");
    }

    @Test
    void updatesDescriptionOfAnAlreadyExistingPermissionWithoutDuplicating() {
        GenAdmProperties properties = new GenAdmProperties();
        properties.setPermissions(List.of(
                new GenAdmProperties.PermissionDefinition("users:read", "New description")
        ));
        PermissionEntity existing = PermissionEntity.builder().code("users:read").description("Old description").build();
        when(permissionRepository.findByCode("users:read")).thenReturn(Optional.of(existing));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        new PermissionCatalogInitializer(properties, permissionRepository).run(null);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getDescription()).isEqualTo("New description");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.startup.PermissionCatalogInitializerTest"`
Expected: FAIL to compile — `GenAdmProperties`/`PermissionCatalogInitializer` don't exist yet.

- [ ] **Step 3: Create `GenAdmProperties`**

`gen-adm-starter/src/main/java/com/example/admsvc/config/properties/GenAdmProperties.java`:
```java
package com.example.admsvc.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * No permission codes are hardcoded anywhere in Gen_ADM — the consuming
 * app supplies its own catalog here, and {@link com.example.admsvc.infrastructure.startup.PermissionCatalogInitializer}
 * upserts it into the {@code permissions} table on startup.
 */
@Data
@ConfigurationProperties(prefix = "gen-adm")
public class GenAdmProperties {

    private List<PermissionDefinition> permissions = new ArrayList<>();

    public record PermissionDefinition(String code, String description) {
    }
}
```

- [ ] **Step 4: Create `PermissionCatalogInitializer`**

`gen-adm-starter/src/main/java/com/example/admsvc/infrastructure/startup/PermissionCatalogInitializer.java`:
```java
package com.example.admsvc.infrastructure.startup;

import com.example.admsvc.config.properties.GenAdmProperties;
import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class PermissionCatalogInitializer implements ApplicationRunner {

    private final GenAdmProperties properties;
    private final PermissionRepository permissionRepository;

    public PermissionCatalogInitializer(GenAdmProperties properties, PermissionRepository permissionRepository) {
        this.properties = properties;
        this.permissionRepository = permissionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (GenAdmProperties.PermissionDefinition definition : properties.getPermissions()) {
            PermissionEntity permission = permissionRepository.findByCode(definition.code())
                    .orElseGet(() -> PermissionEntity.builder().code(definition.code()).build());
            permission.setDescription(definition.description());
            permissionRepository.save(permission);
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.infrastructure.startup.PermissionCatalogInitializerTest"`
Expected: PASS (2 tests, 0 failures).

- [ ] **Step 6: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add consumer-supplied permission catalog with startup upsert"
```

---

## Task 6: REST controllers + global error handling

**Files:**
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateRoleRequest.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/GrantPermissionsRequest.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/AssignRoleRequest.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/RoleResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/PermissionResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/AssignmentResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/ApiErrorResponse.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/controller/RoleController.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/controller/PermissionController.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/controller/AssignmentController.java`
- Create: `gen-adm-starter/src/main/java/com/example/admsvc/api/advice/GlobalExceptionHandler.java`
- Test: `gen-adm-starter/src/test/java/com/example/admsvc/api/controller/RoleControllerTest.java`

**Interfaces:**
- Consumes: `RoleService`, `UserRoleAssignmentService`, `PermissionChecker`, `GenAdmPrincipal` (Tasks 3, 4).
- Produces: `POST/GET/PATCH/DELETE /api/v1/roles`, `PUT /api/v1/roles/{id}/permissions`, `GET /api/v1/permissions`, `POST/DELETE /api/v1/assignments`, `GET /api/v1/assignments` — Task 7's smoke test exercises these routes.

- [ ] **Step 1: Create the DTOs**

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/CreateRoleRequest.java`:
```java
package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleRequest(@NotBlank String name, String description) {
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/GrantPermissionsRequest.java`:
```java
package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record GrantPermissionsRequest(@NotEmpty Set<String> permissionCodes) {
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/request/AssignRoleRequest.java`:
```java
package com.example.admsvc.api.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignRoleRequest(@NotNull UUID userId, @NotNull UUID roleId) {
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/RoleResponse.java`:
```java
package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record RoleResponse(UUID id, String name, String description, Set<String> permissionCodes) {

    public static RoleResponse from(RoleEntity role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions().stream().map(PermissionEntity::getCode).collect(Collectors.toSet()));
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/PermissionResponse.java`:
```java
package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.PermissionEntity;

import java.util.UUID;

public record PermissionResponse(UUID id, String code, String description) {

    public static PermissionResponse from(PermissionEntity permission) {
        return new PermissionResponse(permission.getId(), permission.getCode(), permission.getDescription());
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/AssignmentResponse.java`:
```java
package com.example.admsvc.api.dto.response;

import com.example.admsvc.infrastructure.persistence.entity.UserRoleAssignmentEntity;

import java.util.UUID;

public record AssignmentResponse(UUID userId, UUID roleId) {

    public static AssignmentResponse from(UserRoleAssignmentEntity assignment) {
        return new AssignmentResponse(assignment.getUserId(), assignment.getRoleId());
    }
}
```

`gen-adm-starter/src/main/java/com/example/admsvc/api/dto/response/ApiErrorResponse.java`:
```java
package com.example.admsvc.api.dto.response;

public record ApiErrorResponse(String code, String message) {
}
```

- [ ] **Step 2: Create the global exception handler**

`gen-adm-starter/src/main/java/com/example/admsvc/api/advice/GlobalExceptionHandler.java`:
```java
package com.example.admsvc.api.advice;

import com.example.admsvc.api.dto.response.ApiErrorResponse;
import com.example.admsvc.common.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.admsvc.api.controller")
public class GlobalExceptionHandler {

    @ExceptionHandler(GenAdmNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(GenAdmNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GenAdmConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(GenAdmConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GenAdmValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(GenAdmValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GenAdmForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleForbidden(GenAdmForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(GenAdmConfigException.class)
    public ResponseEntity<ApiErrorResponse> handleConfig(GenAdmConfigException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse(ex.getCode(), ex.getMessage()));
    }
}
```

- [ ] **Step 3: Write the failing `RoleController` test first**

`gen-adm-starter/src/test/java/com/example/admsvc/api/controller/RoleControllerTest.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.advice.GlobalExceptionHandler;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.RoleService;
import com.example.admsvc.common.exception.GenAdmForbiddenException;
import com.example.admsvc.common.exception.GenAdmNotFoundException;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import com.example.admsvc.infrastructure.persistence.entity.RoleEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoleControllerTest {

    private RoleService roleService;
    private PermissionChecker permissionChecker;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    record TestPrincipal(UUID tenantId, UUID userId) implements GenAdmPrincipal {
    }

    private final TestPrincipal principal = new TestPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @BeforeEach
    void setUp() {
        roleService = mock(RoleService.class);
        permissionChecker = mock(PermissionChecker.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RoleController(roleService, permissionChecker))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(principal, null));
    }

    @Test
    void createsARoleWhenPrincipalHasPermission() throws Exception {
        doNothing().when(permissionChecker).require(eq(principal), eq("adm:roles:manage"));
        RoleEntity created = RoleEntity.builder().id(UUID.randomUUID())
                .tenantId(principal.tenantId()).name("owner").build();
        when(roleService.createRole(eq(principal.tenantId()), eq("owner"), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/roles")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                            put("name", "owner");
                            put("description", "Tenant owner");
                        }})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("owner"));
    }

    @Test
    void returns403WhenPrincipalLacksPermission() throws Exception {
        doThrow(new GenAdmForbiddenException("Missing required permission: adm:roles:manage"))
                .when(permissionChecker).require(eq(principal), eq("adm:roles:manage"));

        mockMvc.perform(post("/api/v1/roles")
                        .contentType("application/json")
                        .content("{\"name\":\"owner\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void returns404ForACrossTenantLookup() throws Exception {
        UUID roleId = UUID.randomUUID();
        doNothing().when(permissionChecker).require(eq(principal), eq("adm:roles:manage"));
        when(roleService.getRole(eq(principal.tenantId()), eq(roleId)))
                .thenThrow(new GenAdmNotFoundException("Role not found: " + roleId));

        mockMvc.perform(get("/api/v1/roles/" + roleId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.RoleControllerTest"`
Expected: FAIL to compile — `RoleController` doesn't exist yet.

- [ ] **Step 5: Create `RoleController`**

`gen-adm-starter/src/main/java/com/example/admsvc/api/controller/RoleController.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.CreateRoleRequest;
import com.example.admsvc.api.dto.request.GrantPermissionsRequest;
import com.example.admsvc.api.dto.response.RoleResponse;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.RoleService;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private static final String MANAGE_ROLES = "adm:roles:manage";

    private final RoleService roleService;
    private final PermissionChecker permissionChecker;

    public RoleController(RoleService roleService, PermissionChecker permissionChecker) {
        this.roleService = roleService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public RoleResponse create(@AuthenticationPrincipal GenAdmPrincipal principal,
                                @Valid @RequestBody CreateRoleRequest request) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return RoleResponse.from(roleService.createRole(principal.tenantId(), request.name(), request.description()));
    }

    @GetMapping
    public List<RoleResponse> list(@AuthenticationPrincipal GenAdmPrincipal principal) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return roleService.listRoles(principal.tenantId()).stream().map(RoleResponse::from).toList();
    }

    @GetMapping("/{roleId}")
    public RoleResponse get(@AuthenticationPrincipal GenAdmPrincipal principal, @PathVariable UUID roleId) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return RoleResponse.from(roleService.getRole(principal.tenantId(), roleId));
    }

    @DeleteMapping("/{roleId}")
    public void delete(@AuthenticationPrincipal GenAdmPrincipal principal, @PathVariable UUID roleId) {
        permissionChecker.require(principal, MANAGE_ROLES);
        roleService.deleteRole(principal.tenantId(), roleId);
    }

    @PutMapping("/{roleId}/permissions")
    public RoleResponse replacePermissions(@AuthenticationPrincipal GenAdmPrincipal principal,
                                            @PathVariable UUID roleId,
                                            @Valid @RequestBody GrantPermissionsRequest request) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return RoleResponse.from(roleService.grantPermissions(principal.tenantId(), roleId, request.permissionCodes()));
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :gen-adm-starter:test --tests "com.example.admsvc.api.controller.RoleControllerTest"`
Expected: PASS (3 tests, 0 failures).

- [ ] **Step 7: Create `PermissionController`**

`gen-adm-starter/src/main/java/com/example/admsvc/api/controller/PermissionController.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.response.PermissionResponse;
import com.example.admsvc.infrastructure.persistence.repository.PermissionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionController {

    private final PermissionRepository permissionRepository;

    public PermissionController(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    public List<PermissionResponse> list() {
        return permissionRepository.findAll().stream().map(PermissionResponse::from).toList();
    }
}
```

- [ ] **Step 8: Create `AssignmentController`**

`gen-adm-starter/src/main/java/com/example/admsvc/api/controller/AssignmentController.java`:
```java
package com.example.admsvc.api.controller;

import com.example.admsvc.api.dto.request.AssignRoleRequest;
import com.example.admsvc.api.dto.response.AssignmentResponse;
import com.example.admsvc.application.service.PermissionChecker;
import com.example.admsvc.application.service.UserRoleAssignmentService;
import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {

    private static final String MANAGE_ROLES = "adm:roles:manage";

    private final UserRoleAssignmentService assignmentService;
    private final PermissionChecker permissionChecker;

    public AssignmentController(UserRoleAssignmentService assignmentService, PermissionChecker permissionChecker) {
        this.assignmentService = assignmentService;
        this.permissionChecker = permissionChecker;
    }

    @PostMapping
    public AssignmentResponse assign(@AuthenticationPrincipal GenAdmPrincipal principal,
                                      @Valid @RequestBody AssignRoleRequest request) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return AssignmentResponse.from(
                assignmentService.assignRole(principal.tenantId(), request.userId(), request.roleId()));
    }

    @DeleteMapping
    public void revoke(@AuthenticationPrincipal GenAdmPrincipal principal,
                        @RequestParam UUID userId, @RequestParam UUID roleId) {
        permissionChecker.require(principal, MANAGE_ROLES);
        assignmentService.revokeRole(principal.tenantId(), userId, roleId);
    }

    @GetMapping
    public List<AssignmentResponse> list(@AuthenticationPrincipal GenAdmPrincipal principal,
                                          @RequestParam UUID userId) {
        permissionChecker.require(principal, MANAGE_ROLES);
        return assignmentService.listAssignments(principal.tenantId(), userId).stream()
                .map(AssignmentResponse::from).toList();
    }
}
```

- [ ] **Step 9: Run the full starter test suite**

Run: `./gradlew :gen-adm-starter:test`
Expected: all tests pass.

- [ ] **Step 10: Commit**

```bash
git add gen-adm-starter
git commit -m "feat: add role/permission/assignment REST controllers and global exception handler"
```

---

## Task 7: Demo app wiring, smoke test, docs

**Files:**
- Modify: `gen-adm-demo/src/main/resources/application.yaml` (add datasource + `gen-adm.permissions` config)
- Create: `gen-adm-demo/src/main/java/com/example/gendemo/DemoPrincipalAuthFilter.java`
- Create: `gen-adm-demo/.env.example`
- Create: `scripts/smoke-test.sh`
- Modify: `README.md` (add setup + usage section)

**Interfaces:**
- Consumes: everything from Tasks 1-6.
- Produces: nothing further (this is the final task).

- [ ] **Step 1: Add datasource and permission catalog config to the demo app**

Edit `gen-adm-demo/src/main/resources/application.yaml` — replace its full contents:
```yaml
server:
  port: 8106

spring:
  application:
    name: gen-adm-demo

  datasource:
    url: ${ADM_DB_URL}
    username: ${ADM_DB_USERNAME}
    password: ${ADM_DB_PASSWORD}
    driver-class-name: org.postgresql.Driver

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.PostgreSQLDialect

management:
  endpoints:
    web:
      exposure:
        include:
          - health
          - info

# Consumer-supplied permission catalog — Gen_ADM ships with none built in.
gen-adm:
  permissions:
    - code: adm:roles:manage
      description: Create/edit roles and manage permission grants
    - code: users:read
      description: Read user data (demo-only placeholder code)
```

- [ ] **Step 2: Create a local-only demo auth filter**

`gen-adm-demo/src/main/java/com/example/gendemo/DemoPrincipalAuthFilter.java`:
```java
package com.example.gendemo;

import com.example.admsvc.domain.port.GenAdmPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * FOR LOCAL DEMO/SMOKE-TESTING ONLY — reads tenant/user IDs from plain
 * headers with no real authentication. A real host application wires its
 * own Gen_AUTH-issued-JWT-backed {@code GenAdmPrincipal} here instead.
 */
@Component
public class DemoPrincipalAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tenantHeader = request.getHeader("X-Tenant-Id");
        String userHeader = request.getHeader("X-User-Id");
        if (tenantHeader != null && userHeader != null) {
            GenAdmPrincipal principal = new GenAdmPrincipal() {
                @Override
                public UUID tenantId() {
                    return UUID.fromString(tenantHeader);
                }

                @Override
                public UUID userId() {
                    return UUID.fromString(userHeader);
                }
            };
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(principal, null));
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: Create the demo `.env.example`**

`gen-adm-demo/.env.example`:
```
ADM_DB_URL=jdbc:postgresql://localhost:5432/genadm
ADM_DB_USERNAME=genadm
ADM_DB_PASSWORD=replace_me
```

- [ ] **Step 4: Write the smoke test script**

`scripts/smoke-test.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8106}"
TENANT_ID="$(uuidgen)"
USER_ID="$(uuidgen)"

echo "== Bootstrap first role for a fresh tenant =="
curl -sf -X POST "$BASE_URL/internal/bootstrap" \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"$USER_ID\",\"roleName\":\"owner\",\"permissionCodes\":[\"adm:roles:manage\"]}"
echo

echo "== Create a second role =="
ROLE_ID=$(curl -sf -X POST "$BASE_URL/api/v1/roles" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d '{"name":"viewer","description":"Read-only role"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Created role: $ROLE_ID"

echo "== Assign the new role to a second user =="
OTHER_USER_ID="$(uuidgen)"
curl -sf -X POST "$BASE_URL/api/v1/assignments" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID" \
  -d "{\"userId\":\"$OTHER_USER_ID\",\"roleId\":\"$ROLE_ID\"}"
echo

echo "== List assignments for the second user =="
curl -sf "$BASE_URL/api/v1/assignments?userId=$OTHER_USER_ID" \
  -H "X-Tenant-Id: $TENANT_ID" -H "X-User-Id: $USER_ID"
echo

echo "Smoke test complete."
```

Note: `POST /internal/bootstrap` does not exist as a controller route in this
plan (`bootstrapTenant` is programmatic-only per the spec) — for the demo
app specifically, add a minimal `@RestController` wrapping it so the smoke
script has something to call:

`gen-adm-demo/src/main/java/com/example/gendemo/DemoBootstrapController.java`:
```java
package com.example.gendemo;

import com.example.admsvc.application.service.UserRoleAssignmentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Demo-only wrapper around the programmatic bootstrapTenant path — a real
 * host app calls UserRoleAssignmentService.bootstrapTenant(...) directly
 * from its own tenant-provisioning code, not over HTTP.
 */
@RestController
public class DemoBootstrapController {

    record BootstrapRequest(UUID tenantId, UUID userId, String roleName, Set<String> permissionCodes) {
    }

    private final UserRoleAssignmentService assignmentService;

    public DemoBootstrapController(UserRoleAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping("/internal/bootstrap")
    public void bootstrap(@RequestBody BootstrapRequest request) {
        assignmentService.bootstrapTenant(
                request.tenantId(), request.userId(), request.roleName(), request.permissionCodes());
    }
}
```

- [ ] **Step 5: Add a Captcha-adjacent README section for Gen_ADM**

Add to `README.md` (create the file if it does not already exist, with a top-level `# Gen_ADM` heading first):
```markdown
## RBAC (Phase 1)

Tenant-scoped roles, a consumer-supplied permission catalog, user-role
assignments, and Postgres RLS for tenant isolation.

- No built-in roles or permissions — supply your own catalog via
  `gen-adm.permissions` in `application.yaml`.
- `UserRoleAssignmentService.bootstrapTenant(...)` is the only way to create
  a fresh tenant's first role — call it in-process from your own
  tenant-provisioning flow, there is no HTTP route for it in the starter
  itself.
- Every other mutating endpoint requires the `adm:roles:manage` permission.
- See `docs/superpowers/specs/2026-07-23-gen-adm-rbac-design.md` for the
  full design.

### Local smoke test

```bash
cd gen-adm-demo
cp .env.example .env
# fill in ADM_DB_URL/ADM_DB_USERNAME/ADM_DB_PASSWORD
../gradlew bootRun &
sleep 5
../scripts/smoke-test.sh
```
```

- [ ] **Step 6: Run the full build**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add gen-adm-demo README.md scripts
git commit -m "feat: wire gen-adm-demo with real datasource, demo auth filter, and smoke test"
```

---

## Task 8: Dockerfile for gen-adm-demo

**Files:**
- Create: `Dockerfile`

**Interfaces:**
- Consumes: `gen-adm-demo`'s `bootJar` Gradle task, `settings.gradle`, both modules' `build.gradle` (Tasks 1-7).
- Produces: nothing later tasks depend on — this is a leaf deployment artifact.

Gen_AUTH's own `Dockerfile` references paths from an earlier CPMS-Platform
monorepo layout (`libs/java-common`, `apps/auth-svc`, root
`build.gradle.kts`/`settings.gradle.kts`) that don't match its own actual
current structure — it is not a working pattern to copy. This task writes a
correct one against Gen_ADM's real module layout instead.

- [ ] **Step 1: Create the Dockerfile**

`Dockerfile`:
```dockerfile
# ══════════════════════════════════════════════════════════════
# Gen_ADM demo — multi-stage build
# Build context: repo root
# ══════════════════════════════════════════════════════════════
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY settings.gradle .
COPY gen-adm-starter gen-adm-starter
COPY gen-adm-demo gen-adm-demo

RUN chmod +x gradlew && ./gradlew :gen-adm-demo:bootJar --no-daemon

# ── Runtime Stage ─────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /app/gen-adm-demo/build/libs/*.jar app.jar

RUN chown -R app:app /app
USER app

EXPOSE 8106

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8106/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
```

- [ ] **Step 2: Build the image**

Run: `docker build -t gen-adm-demo:local .`
Expected: image builds successfully (this runs the full Gradle build inside
the container, so it also re-verifies Tasks 1-7's code compiles and tests
pass in a clean environment).

- [ ] **Step 3: Commit**

```bash
git add Dockerfile
git commit -m "feat: add Dockerfile for gen-adm-demo"
```

---

## Task 9: Integration guide

**Files:**
- Create: `docs/integration-guide.md`
- Modify: `README.md` (link to the new guide)

**Interfaces:**
- Consumes: everything from Tasks 1-8 (this task only documents existing behavior, adds no code).

- [ ] **Step 1: Write the integration guide**

`docs/integration-guide.md`:
```markdown
# Gen_ADM Integration Guide

Tenant-scoped RBAC as a Spring Boot library. Add `gen-adm-starter` as a
dependency and `GenAdmAutoConfiguration` wires everything in automatically.

## 1. Embedding

Add the dependency (from GitHub Packages — see this repo's root
`build.gradle`/`settings.gradle` for the exact coordinates):

```groovy
implementation 'com.example:gen-adm-starter:0.1.0-SNAPSHOT'
```

Gen_ADM requires:
- A PostgreSQL datasource (`spring.datasource.*`) — it runs its own Flyway
  migrations from `classpath:db/migration/genadm`, independent of any
  migrations your own app runs.
- `gen-adm.permissions` in your config — Gen_ADM ships with **no built-in
  permissions**; you supply your own catalog:

```yaml
gen-adm:
  permissions:
    - code: adm:roles:manage
      description: Create/edit roles and manage permission grants
    - code: users:read
      description: Read user data
```

- Your own `Authentication#getPrincipal()` implementing
  `com.example.admsvc.domain.port.GenAdmPrincipal` (`tenantId()`,
  `userId()`) — Gen_ADM never issues or verifies tokens itself; it reads
  whatever principal your own auth layer (e.g. Gen_AUTH) already populated
  in `SecurityContextHolder`.

## 2. Bootstrapping a tenant's first role

There is no HTTP route for this — call it directly, in-process, from your
own tenant-provisioning flow:

```java
UUID assignmentId = userRoleAssignmentService.bootstrapTenant(
    tenantId, firstUserId, "owner", Set.of("adm:roles:manage"));
```

A second call for a tenant that already has roles throws
`GenAdmConflictException` — this path is for first-role creation only.

## 3. HTTP API reference

All routes require an already-resolved `GenAdmPrincipal` and (except
bootstrap, which has no route) a `PermissionChecker.require(...)` check —
by default every mutating endpoint requires `adm:roles:manage`.

| Method | Path | Body | Notes |
|---|---|---|---|
| POST | `/api/v1/roles` | `{name, description}` | Creates a role |
| GET | `/api/v1/roles` | — | Lists roles for the caller's tenant |
| GET | `/api/v1/roles/{id}` | — | 404 if not found or belongs to another tenant |
| DELETE | `/api/v1/roles/{id}` | — | |
| PUT | `/api/v1/roles/{id}/permissions` | `{permissionCodes: [...]}` | Replaces the role's permission grants |
| GET | `/api/v1/permissions` | — | Lists the consumer-supplied global catalog |
| POST | `/api/v1/assignments` | `{userId, roleId}` | 409 if the user already holds that role |
| DELETE | `/api/v1/assignments?userId=&roleId=` | — | |
| GET | `/api/v1/assignments?userId=` | — | |

## 4. Error reference

| Code | HTTP status | Meaning |
|---|---|---|
| `NOT_FOUND` | 404 | Resource doesn't exist, or belongs to another tenant |
| `CONFLICT` | 409 | Duplicate role name, duplicate assignment, or bootstrap called on an already-provisioned tenant |
| `VALIDATION_ERROR` | 400 | Unknown permission code, missing required field |
| `FORBIDDEN` | 403 | Caller's principal lacks the required permission |
| `CONFIG_ERROR` | 500 | No resolvable tenant context (misconfigured caller, not the caller's fault) |

## 5. Local smoke test

```bash
cd gen-adm-demo
cp .env.example .env
# fill in ADM_DB_URL/ADM_DB_USERNAME/ADM_DB_PASSWORD
../gradlew bootRun &
sleep 5
../scripts/smoke-test.sh
```

See `docs/superpowers/specs/2026-07-23-gen-adm-rbac-design.md` for the full
design rationale.
```

- [ ] **Step 2: Link the guide from the README**

Add this line to `README.md`, right after the `## RBAC (Phase 1)` heading
added in Task 7:
```markdown
Full integration instructions: [docs/integration-guide.md](docs/integration-guide.md)
```

- [ ] **Step 3: Commit**

```bash
git add docs/integration-guide.md README.md
git commit -m "docs: add Gen_ADM integration guide"
```

---

## Final Step (after all 9 tasks)

Run the full test suite one more time (`./gradlew build`) and confirm every
module's tests pass before the final whole-branch review described in the
Global Constraints section.
