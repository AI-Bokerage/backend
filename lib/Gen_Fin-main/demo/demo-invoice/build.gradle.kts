plugins {
    id("java")
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = providers.gradleProperty("genfin.group").get()
version = providers.gradleProperty("genfin.version").get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation(project(":fin-spring-boot-starter"))
    implementation(project(":fin-invoice"))
}
