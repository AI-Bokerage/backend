plugins {
    id("java")
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

extensions.configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    implementation(project(":audit-spring-boot-starter"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Boot 4 split the old monolithic spring-boot-autoconfigure jar by feature; a real consuming
    // app must bring these itself — the starter only declares them compileOnly (see its
    // build.gradle.kts), matching the standard Spring Boot starter dependency pattern.
    implementation("org.springframework.boot:spring-boot-persistence")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation(platform("software.amazon.awssdk:bom:2.27.21"))
    implementation("software.amazon.awssdk:s3")
    runtimeOnly("org.postgresql:postgresql")
}

tasks.named("bootJar") {
    enabled = true
}

// This is a manual-poking demo app only — never published, no automated tests live here.
tasks.matching { it.name == "publish" || it.name == "publishToMavenLocal" }.configureEach {
    enabled = false
}
