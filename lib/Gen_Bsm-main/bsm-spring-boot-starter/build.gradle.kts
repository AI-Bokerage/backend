// bsm-spring-boot-starter — auto-configuration for bsm-core. Add this one dependency to any
// Spring Boot app, implement the required Ports as beans, and the Billing application services
// wire themselves up automatically.
plugins {
    `java-library`
    id("io.spring.dependency-management") version "1.1.7"
    `maven-publish`
}

group = "com.company"
version = "0.0.1-SNAPSHOT"
description = "bsm-spring-boot-starter — auto-configuration for bsm-core"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.6")
    }
}

dependencies {
    api(project(":bsm-core"))

    implementation("org.springframework.boot:spring-boot-autoconfigure")
    annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

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
                name.set("bsm-spring-boot-starter")
                description.set(project.description)
            }
        }
    }
}

tasks.named<GenerateModuleMetadata>("generateMetadataFileForMavenPublication") {
    // Same io.spring.dependency-management / Gradle Module Metadata interaction bsm-core
    // documents — versions are fully resolved at build time via BOM import, but the validator
    // can't see that and flags `api`/`implementation` dependencies as "unversioned".
    suppressedValidationErrors.add("dependencies-without-versions")
}
