// java-common — plain library — no Spring Boot fat-jar plugin.
//
// Copied from CPMS-Platform/libs/java-common (see ../../CPMS-Platform in the workspace) so bsm-svc
// can resolve `io.cpms.common.*`, which it actively imports (security, messaging types). The
// original CPMS-Platform repo relies on its own root build.gradle.kts (`subprojects {}`) to apply
// `io.spring.dependency-management` + the spring-boot-dependencies BOM and to manage the jjwt
// version — that convention script doesn't exist here, so both are declared directly in this file
// instead. Not otherwise modified from the source copy.
plugins {
    `java-library`
    id("io.spring.dependency-management") version "1.1.7"
    `maven-publish`
}

group = "io.cpms"
version = "0.1.0-SNAPSHOT"
description = "java-common — shared errors/security/messaging/plan types (copied from CPMS-Platform)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
    }
}

val jjwtVersion = "0.12.6"

dependencies {
    // Platform-neutral authenticated-principal contract (Phase 2.6)
    api(project(":libs:security-spi"))

    // Spring context and web for shared annotations / request handling
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.aspectj:aspectjweaver")

    // JWT (shared token handling) — not managed by the spring-boot-dependencies BOM, needs a literal version
    api("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // Messaging abstractions (consumers/producers share these types)
    api("org.springframework.boot:spring-boot-starter-amqp")

    // JWT authorization for downstream services (JWKS-backed RS256 validation)
    api("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // Redis/Valkey — runtime RBAC resolution (role:{roleId} → permission codes)
    api("org.springframework.boot:spring-boot-starter-data-redis")

    // Lombok (optional — remove if you prefer records/plain classes)
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test support exported to consumers
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            pom {
                name.set("java-common")
                description.set(project.description)
            }
        }
    }
}
