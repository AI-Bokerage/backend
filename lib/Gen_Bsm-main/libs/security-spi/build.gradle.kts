// security-spi — platform-neutral authentication contracts.
//
// Zero framework dependencies, zero platform branding. Any host application's security layer
// (CPMS, or any other SaaS platform) provides an implementation of these interfaces; reusable
// libraries and adapters depend only on the contract, never on a specific platform's principal
// model or JWT claim shape.
plugins {
    `java-library`
    `maven-publish`
}

group = "io.platform"
version = "0.1.0-SNAPSHOT"
description = "security-spi — platform-neutral authenticated-principal contract"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
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
                name.set("security-spi")
                description.set(project.description)
            }
        }
    }
}
