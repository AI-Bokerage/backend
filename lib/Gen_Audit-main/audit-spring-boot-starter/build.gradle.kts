// A library, never run — the Spring Boot Gradle plugin is intentionally not applied here.
// It targets Java 21 to match the real consuming services' (aud-svc / audit-svc) baseline,
// confirmed against CPMS-Platform's own build.gradle.kts rather than assumed.
plugins {
    id("java-library")
}

// First explicit version for this module — versioned independently from audit-core, since
// they're separate published artifacts with separate compatibility surfaces even though this
// one depends on the other. Starts at 0.1.0, not "1.0.0 to match the milestone": this module has
// never had a version before, so there is no prior release to be additive/breaking relative to;
// picking a number to make Phase 7 feel bigger than it is would violate the same mechanical
// versioning rule audit-core follows — see VERSIONING.md.
//
// Retroactively corrected by a release-readiness audit: Phase 8 and Phase 9 each added
// backward-compatible surface to this module without bumping the version at the time, an
// oversight, not a deliberate exception to the mechanical rule above. Applying that same rule
// now, after the fact: 0.1.0 -> 0.2.0 (Phase 8 — AuditMetricsRecorder gained
// recordPartitionLockWait/recordStalenessRejection/recordLockTimeout, all additive) -> 0.3.0
// (Phase 9 — sharding.PartitionShardResolver and its default implementation, plus
// AuditMetricsRecorder gaining recordShardOwnershipAccepted/recordShardMismatch, all additive)
// -> 0.3.1 (Phase 9's message-converter fix — RabbitEventConsumer's ParameterizedTypeReference
// correction — is a bug fix to a listener implementation detail, not a change to any documented
// public surface, so patch rather than minor). See VERSIONING.md and CHANGELOG.md for the
// full reasoning.
version = "0.3.1"

extensions.configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

tasks.test {
    maxHeapSize = "384m"
    maxParallelForks = 1
}

dependencies {
    api(project(":audit-core"))
    api(platform("org.springframework.boot:spring-boot-dependencies:4.0.6"))
    api("org.springframework.boot:spring-boot-autoconfigure")
    compileOnly("org.springframework.boot:spring-boot-starter-data-jpa")
    // Boot 4 split the old monolithic spring-boot-autoconfigure jar by feature; EntityScan and
    // FlywayConfigurationCustomizer now live in these two feature-specific modules.
    compileOnly("org.springframework.boot:spring-boot-persistence")
    compileOnly("org.springframework.boot:spring-boot-flyway")
    compileOnly("org.flywaydb:flyway-database-postgresql")
    compileOnly("org.springframework.boot:spring-boot-starter-data-mongodb")
    compileOnly("org.springframework.boot:spring-boot-starter-amqp")
    // RabbitEventConsumer's JSON message conversion needs a Jackson-2 ObjectMapper with the
    // JSR-310 module registered for java.time types. Boot 4's own Jackson auto-configuration
    // (spring-boot-jackson) produces a Jackson-3 tools.jackson.databind.ObjectMapper — a
    // different class entirely, not assignable to what Jackson2JsonMessageConverter needs — so
    // there is no Boot-managed bean to inject here; a dedicated Jackson-2 ObjectMapper is built
    // explicitly in AuditRabbitAutoConfiguration instead. Found via an actual
    // NoSuchBeanDefinitionException naming the Jackson-2 type, not assumed.
    compileOnly("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    compileOnly("io.micrometer:micrometer-core")
    // Boot 4 moved the health-contributor API (HealthIndicator, Health, Status) out of
    // spring-boot-actuator entirely, into its own spring-boot-health module — confirmed by
    // inspecting the resolved spring-boot-actuator-4.0.6.jar and finding no
    // org/springframework/boot/actuate/health package at all, not assumed. spring-boot-actuator
    // alone does not pull spring-boot-health transitively; spring-boot-starter-actuator does.
    compileOnly("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("org.springframework.boot:spring-boot-starter-validation")
    compileOnly("jakarta.validation:jakarta.validation-api")
    // The AWS SDK v2 version is NOT managed by spring-boot-dependencies — confirmed by grepping
    // that BOM's POM for "aws" and finding nothing, not assumed. Its own BOM is imported
    // separately for consistent module versions across the S3 client and anything AWS-related.
    compileOnly(platform("software.amazon.awssdk:bom:2.27.21"))
    compileOnly("software.amazon.awssdk:s3")
    annotationProcessor(platform("org.springframework.boot:spring-boot-dependencies:4.0.6"))
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-persistence")
    testImplementation("org.springframework.boot:spring-boot-flyway")
    testImplementation("org.flywaydb:flyway-database-postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    testImplementation("org.springframework.boot:spring-boot-starter-amqp")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    testImplementation("io.micrometer:micrometer-core")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-validation")
    testImplementation(platform("software.amazon.awssdk:bom:2.27.21"))
    testImplementation("software.amazon.awssdk:s3")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.20.1"))
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:localstack")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.postgresql:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
