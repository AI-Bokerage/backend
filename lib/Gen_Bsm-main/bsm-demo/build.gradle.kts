// bsm-demo — an independent Spring Boot application proving bsm-core is a genuine,
// reusable Billing & Subscription Management library. Depends on bsm-spring-boot-starter
// (which brings bsm-core transitively) and security-spi — never on bsm-svc or any
// CPMS-specific adapter. Phase 6: migrated from manual @Bean wiring to the starter's
// auto-configuration — see ARCHITECTURE_CERTIFICATION.md.
plugins {
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    java
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "bsm-demo — second-consumer validation for bsm-core"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":bsm-spring-boot-starter"))
    implementation(project(":libs:security-spi"))

    implementation("org.springframework.boot:spring-boot-starter")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
